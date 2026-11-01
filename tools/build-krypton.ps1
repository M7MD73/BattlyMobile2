param(
    [string[]]$Abis = @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'),
    [int]$Jobs = 6
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'build/krypton-source'
$revision = '472f55e549e2bc76c87454905db52661c3cf57a9'
$sdk = $env:ANDROID_HOME
if (!$sdk) { throw 'ANDROID_HOME is required' }
$cmake = Join-Path $sdk 'cmake/3.31.6/bin/cmake.exe'
$ninja = Join-Path $sdk 'cmake/3.31.6/bin/ninja.exe'
$ndk = Join-Path $sdk 'ndk/27.3.13750724'
if (!(Test-Path -LiteralPath $source)) {
    git clone https://github.com/FCL-Team/NG-GL4ES.git $source
    if ($LASTEXITCODE) { throw 'Krypton clone failed' }
    git -C $source checkout --detach $revision
    if ($LASTEXITCODE) { throw 'Krypton checkout failed' }
}
$head = git -C $source rev-parse HEAD
if ($head -ne $revision) { throw "Expected source revision $revision; found $head" }
git -C $source submodule update --init --recursive
if ($LASTEXITCODE) { throw 'Krypton submodules failed' }
foreach ($patch in Get-ChildItem (Join-Path $PSScriptRoot 'krypton/patches') -Filter '*.patch' | Sort-Object Name) {
    git -C $source apply --reverse --check $patch.FullName 2>$null
    if ($LASTEXITCODE -ne 0) {
        git -C $source apply --check $patch.FullName
        if ($LASTEXITCODE) { throw "Cannot apply Krypton patch: $($patch.Name)" }
        git -C $source apply $patch.FullName
        if ($LASTEXITCODE) { throw "Krypton patch failed: $($patch.Name)" }
    }
}
foreach ($abi in $Abis) {
    if ($abi -notin @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86')) { throw "Invalid ABI: $abi" }
    $build = Join-Path $root "build/krypton-native/$abi"
    & $cmake -S $source -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" `
        "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" "-DANDROID_ABI=$abi" `
        -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release `
        -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON
    if ($LASTEXITCODE) { throw "Krypton configure failed: $abi" }
    & $cmake --build $build --target ng_gl4es -j $Jobs
    if ($LASTEXITCODE) { throw "Krypton build failed: $abi" }
}

# Publish only complete multi-ABI archives; partial builds never overwrite the shipped AAR.
$allAbis = @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86')
foreach ($abi in $allAbis) {
    if (!(Test-Path -LiteralPath (Join-Path $root "build/krypton-native/$abi/libng_gl4es.so"))) {
        Write-Output "Compiled requested ABIs; $abi is still needed before packaging."
        exit 0
    }
}
Add-Type -AssemblyName System.IO.Compression
$output = Join-Path $root 'app_pojavlauncher/libs/krypton-wrapper-0.4.5.aar'
$temporary = Join-Path $root 'build/krypton-wrapper-0.4.5.aar'
$stream = [System.IO.File]::Open($temporary, [System.IO.FileMode]::Create)
$zip = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($abi in $allAbis) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,
            (Join-Path $root "build/krypton-native/$abi/libng_gl4es.so"), "jni/$abi/libng_gl4es.so") | Out-Null
    }
    $manifest = $zip.CreateEntry('AndroidManifest.xml').Open()
    $writer = [System.IO.StreamWriter]::new($manifest)
    $writer.Write('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.battly.krypton"><uses-sdk android:minSdkVersion="21" /></manifest>')
    $writer.Dispose()
    foreach ($license in @('LICENSE', 'src/gl/cJSON.h', '3rdparty/SPIRV-Cross/LICENSE', '3rdparty/glslang/LICENSE.txt', '3rdparty/glslang/LICENSES/BSD-3-Clause.txt', '3rdparty/glslang/LICENSES/Apache-2.0.txt')) {
        $path = Join-Path $source $license
        if (Test-Path -LiteralPath $path) {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $path,
                "assets/licenses/krypton/$license") | Out-Null
        }
    }
} finally { $zip.Dispose(); $stream.Dispose() }
Copy-Item -LiteralPath $temporary -Destination $output -Force
Get-FileHash -LiteralPath $output -Algorithm SHA256
