$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $root
try {
    $agent = 'app_pojavlauncher/src/main/assets/components/lwjgl3/lwjgl-patch-agent.jar'
    & javac -cp $agent -d build tools/GlfwSizeBridgeProbe.java tools/ShaderJarRepairProbe.java tools/CodecContractProbe.java tools/MoreCosmeticsConsoleProbe.java
    if ($LASTEXITCODE) { throw 'Probe compilation failed' }
    & java -cp "$agent;build" GlfwSizeBridgeProbe $root
    if ($LASTEXITCODE) { throw 'Window-size regression failed' }
    $classes = 'app_pojavlauncher/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA/Android/Sdk" }
    $android = (Get-ChildItem "$sdk/platforms" -Recurse -Filter android.jar | Sort-Object FullName | Select-Object -Last 1).FullName
    & java -cp "$agent;build;$classes;$android" ShaderJarRepairProbe $classes
    if ($LASTEXITCODE) { throw 'Shader JAR repair failed' }
    if (!(Test-Path 'build/voicechat-2.6.11.jar') -or !(Test-Path 'build/morecosmetics-1.3-mc1.21.11.jar')) {
        throw 'Download the exact official Modrinth fixtures documented in docs/pending-video-shaders-codecs.md first'
    }
    & java -cp "$agent;build" CodecContractProbe $root build/voicechat-2.6.11.jar
    if ($LASTEXITCODE) { throw 'Voice Chat JNI contracts failed' }
    & java '-Djava.awt.headless=true' -cp build MoreCosmeticsConsoleProbe build/morecosmetics-1.3-mc1.21.11.jar
    if ($LASTEXITCODE) { throw 'MoreCosmetics console initialization failed' }
} finally { Pop-Location }
