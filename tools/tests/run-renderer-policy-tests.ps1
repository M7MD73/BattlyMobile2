param([string]$ClangDirectory = 'C:/Program Files/LLVM/bin')
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = Join-Path $root 'build/renderer-policy-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& (Join-Path $ClangDirectory 'clang.exe') (Join-Path $PSScriptRoot 'sdl_surface_policy_test.c') -o "$output/sdl-surface.exe"
if ($LASTEXITCODE) { throw 'SDL policy test compilation failed' }
& "$output/sdl-surface.exe"
if ($LASTEXITCODE) { throw 'SDL policy tests failed' }
$mg = Join-Path $root 'MobileGlues/MobileGlues-cpp'
& (Join-Path $ClangDirectory 'clang++.exe') -std=c++17 -I "$mg/include" -iquote $mg "$mg/tests/buffer_mapping_test.cpp" -o "$output/buffer-mapping.exe"
if ($LASTEXITCODE) { throw 'Mapping policy test compilation failed' }
& "$output/buffer-mapping.exe"
if ($LASTEXITCODE) { throw 'Mapping policy tests failed' }
