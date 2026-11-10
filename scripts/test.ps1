$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'build.ps1')
$taskClasses = Join-Path $taskRepo 'build/classes'
$taskTests = Join-Path $taskRepo 'build/test-classes'
New-Item -ItemType Directory -Force -Path $taskTests | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'src/test/java/pzcloud') -Filter '*.java' -File | ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -cp $taskClasses -d $taskTests @taskSources
if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion de pruebas.' }
$taskClasspath = $taskClasses + [IO.Path]::PathSeparator + $taskTests
& java '-Djava.awt.headless=true' -cp $taskClasspath pzcloud.SelfTest
if ($LASTEXITCODE -ne 0) { throw 'Fallo una prueba.' }
