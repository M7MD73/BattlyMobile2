param([string[]]$Abis = @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'))
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA/Android/Sdk" }
$cmake = "$sdk/cmake/3.31.6/bin/cmake.exe"
$ninja = "$sdk/cmake/3.31.6/bin/ninja.exe"
$ndk = "$sdk/ndk/27.3.13750724"
$revisions = [ordered]@{
    opus4j = '362db14553d39c4dc080e4edd50630b17dbf9682'
    speex4j = '9428cb14b8ec373fcd36f5b5792899611042ece9'
    lame4j = 'b09599989878cbc7349075eca0b3aa4f105e5957'
    rnnoise4j = '5f2097d504fc67f31d0d8900e9df0a5bdf4ec9ae'
}
foreach ($codec in $revisions.Keys) {
    $source = Join-Path $root "build/$codec-source"
    if (!(Test-Path "$source/.git")) {
        & git clone "https://github.com/henkelmax/$codec.git" $source
        if ($LASTEXITCODE) { throw "Clone failed: $codec" }
        & git -C $source checkout --detach $revisions[$codec]
        if ($LASTEXITCODE) { throw "Checkout failed: $codec" }
    }
    if ((& git -C $source rev-parse HEAD) -ne $revisions[$codec]) { throw "Unexpected $codec revision" }
    foreach ($abi in $Abis) {
        $build = Join-Path $root "build/android-codecs/$abi/$codec"
        $extra = @()
        if ($codec -eq 'lame4j') {
            $mp3 = Join-Path $root 'build/codecs-minimp3'
            if (!(Test-Path "$mp3/.git")) {
                & git clone https://github.com/lieff/minimp3.git $mp3
                if ($LASTEXITCODE) { throw 'minimp3 clone failed' }
                & git -C $mp3 checkout --detach ea99364f61c14656440e8d77e9c233ccf3124633
                if ($LASTEXITCODE) { throw 'minimp3 checkout failed' }
            }
            if ((& git -C $mp3 rev-parse HEAD) -ne 'ea99364f61c14656440e8d77e9c233ccf3124633') { throw 'Unexpected minimp3 revision' }
            $extra += "-DFETCHCONTENT_SOURCE_DIR_MINIMP3=$mp3"
        }
        & $cmake -S "$source/native" -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" `
            "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" "-DANDROID_ABI=$abi" `
            -DANDROID_PLATFORM=android-21 -DCMAKE_BUILD_TYPE=Release -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON `
            "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--version-script=$root/tools/codecs/exports.map" `
            -DOPUS_BUILD_SHARED_LIBRARY=OFF -DOPUS_BUILD_TESTING=OFF @extra
        if ($LASTEXITCODE) { throw "Configure failed: $codec/$abi" }
        & $cmake --build $build --target $codec -j 6
        if ($LASTEXITCODE) { throw "Build failed: $codec/$abi" }
        & "$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe" --strip-debug "$build/lib$codec.so"
        if ($LASTEXITCODE) { throw "Strip failed: $codec/$abi" }
    }
}
Add-Type -AssemblyName System.IO.Compression
$temporary = Join-Path $root 'build/android-audio-codecs.aar'
$stream = [IO.File]::Open($temporary, [IO.FileMode]::Create)
$zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($codec in $revisions.Keys) {
        foreach ($abi in $Abis) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,
                "$root/build/android-codecs/$abi/$codec/lib$codec.so", "jni/$abi/lib$codec.so") | Out-Null
        }
        Get-ChildItem "$root/build/$codec-source/src/main/resources" -File | Where-Object Name -Match 'LICENSE|COPYING' | ForEach-Object {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, "assets/licenses/android-codecs/$($_.Name)") | Out-Null
        }
    }
    $writer = [IO.StreamWriter]::new($zip.CreateEntry('AndroidManifest.xml').Open())
    $writer.Write('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.battly.audio.codecs"><uses-sdk android:minSdkVersion="21" /></manifest>')
    $writer.Dispose()
} finally { $zip.Dispose(); $stream.Dispose() }
Copy-Item -LiteralPath $temporary -Destination "$root/app_pojavlauncher/libs/android-audio-codecs.aar" -Force
