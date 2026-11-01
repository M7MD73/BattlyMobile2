param([int]$Jobs = 6)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'build/axiom-imgui-source'
$revision = '4a2813430cc2bc64ba2106289d0c8606e656f32d'
$sdk = $env:ANDROID_HOME
if (!$sdk) { throw 'ANDROID_HOME is required' }
$cmake = Join-Path $sdk 'cmake/3.31.6/bin/cmake.exe'
$ninja = Join-Path $sdk 'cmake/3.31.6/bin/ninja.exe'
$ndk = Join-Path $sdk 'ndk/27.3.13750724'
if (!(Test-Path -LiteralPath $source)) {
    git clone --depth 1 --branch v1.92-moulberry.2 https://github.com/Moulberry/imgui-java.git $source
    if ($LASTEXITCODE) { throw 'ImGui clone failed' }
}
if ((git -C $source rev-parse HEAD) -ne $revision) { throw 'Unexpected ImGui source revision' }
git -C $source submodule update --init --recursive
if ($LASTEXITCODE) { throw 'ImGui submodules failed' }
foreach ($patch in Get-ChildItem (Join-Path $source 'patches') -Filter '*.patch') {
    $vendor = Join-Path $source 'include/imgui-node-editor'
    git -C $vendor apply --reverse --check $patch.FullName 2>$null
    if ($LASTEXITCODE -ne 0) {
        git -C $vendor apply --check $patch.FullName
        if ($LASTEXITCODE) { throw 'Unexpected vendor source' }
        git -C $vendor apply $patch.FullName
        if ($LASTEXITCODE) { throw 'Vendor patch failed' }
    }
}
Push-Location $source
try {
    & ./gradlew.bat :imgui-binding:generateAndroidJni -I (Join-Path $PSScriptRoot 'imgui/android-jni.gradle') --console=plain
    if ($LASTEXITCODE) { throw 'JNI generation failed' }
} finally { Pop-Location }
$abis = @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86')
foreach ($abi in $abis) {
    $build = Join-Path $root "build/imgui-moulberry92/$abi"
    & $cmake -S (Join-Path $PSScriptRoot 'imgui') -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" `
        "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" "-DANDROID_ABI=$abi" `
        -DANDROID_PLATFORM=android-21 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release `
        "-DIMGUI_SOURCE=$($source.Replace('\', '/'))" -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON
    if ($LASTEXITCODE) { throw "ImGui configure failed: $abi" }
    & $cmake --build $build -j $Jobs
    if ($LASTEXITCODE) { throw "ImGui build failed: $abi" }
    & (Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe') --strip-debug (Join-Path $build 'libimgui-moulberry92-java.so')
    if ($LASTEXITCODE) { throw "ImGui strip failed: $abi" }
}
Add-Type -AssemblyName System.IO.Compression
$temporary = Join-Path $root 'build/imgui-moulberry92-1.92-moulberry.2.aar'
$stream = [IO.File]::Open($temporary, [IO.FileMode]::Create)
$zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($abi in $abis) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,
            (Join-Path $root "build/imgui-moulberry92/$abi/libimgui-moulberry92-java.so"),
            "jni/$abi/libimgui-moulberry92-java.so") | Out-Null
    }
    $writer = [IO.StreamWriter]::new($zip.CreateEntry('AndroidManifest.xml').Open())
    $writer.Write('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.battly.imgui.moulberry92"><uses-sdk android:minSdkVersion="21" /></manifest>')
    $writer.Dispose()
    $licenses = @((Join-Path $source 'LICENSE')) + @(Get-ChildItem (Join-Path $source 'include') -Recurse -File |
        Where-Object { $_.Name -match '^(LICENSE|COPYING)(\..*)?$' } | ForEach-Object FullName)
    foreach ($license in $licenses) {
        $relative = [IO.Path]::GetRelativePath($source, $license).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $license,
            "assets/licenses/imgui-moulberry92/$relative") | Out-Null
    }
} finally { $zip.Dispose(); $stream.Dispose() }
$output = Join-Path $root 'app_pojavlauncher/libs/imgui-moulberry92-1.92-moulberry.2.aar'
Copy-Item -LiteralPath $temporary -Destination $output -Force
Get-FileHash -LiteralPath $output -Algorithm SHA256
