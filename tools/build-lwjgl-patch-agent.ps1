$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'build/patch-agent-classes'
$agent = Join-Path $root 'app_pojavlauncher/src/main/assets/components/lwjgl3/lwjgl-patch-agent.jar'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @('LwjglPatchAgent.java', 'TouchLayoutCompat.java', 'CodecNativeTransformer.java') | ForEach-Object {
    Join-Path $root "jre_lwjgl3glfw/agent/net/kdt/patch/$_"
}
& javac -source 8 -target 8 -encoding UTF-8 -cp $agent -d $output $sources
if ($LASTEXITCODE) { throw 'Patch agent compilation failed' }
& jar uf $agent -C $output .
if ($LASTEXITCODE) { throw 'Patch agent packaging failed' }
$resources = Join-Path $root 'jre_lwjgl3glfw/agent-resources'
if (Test-Path -LiteralPath $resources) {
    & jar uf $agent -C $resources .
    if ($LASTEXITCODE) { throw 'Patch agent resources failed' }
}
