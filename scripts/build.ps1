param([switch]$Package)
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
$taskBuild = Join-Path $taskRepo 'build'
$taskClasses = Join-Path $taskBuild 'classes'
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Instala JDK 21: winget install Microsoft.OpenJDK.21. Luego abri otra terminal.' }
if (Test-Path $taskClasses) { Remove-Item -LiteralPath $taskClasses -Recurse -Force }
New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'src/main/java/pzcloud') -Filter '*.java' -File | ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -d $taskClasses @taskSources
if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion.' }
$taskJar = Join-Path $taskBuild 'PZCloudManagement.jar'
& jar --create --file $taskJar --main-class pzcloud.PzCloudApp -C $taskClasses . -C $taskRepo mod
if ($LASTEXITCODE -ne 0) { throw 'Fallo la creacion del JAR.' }
Write-Host "JAR listo: $taskJar"
if ($Package) {
    $taskInput = Join-Path $taskBuild 'package-input'
    $taskPortable = Join-Path $taskBuild 'portable'
    New-Item -ItemType Directory -Force -Path $taskInput | Out-Null
    Copy-Item -LiteralPath $taskJar -Destination $taskInput -Force
    if (Test-Path $taskPortable) { Remove-Item -LiteralPath $taskPortable -Recurse -Force }
    & jpackage --type app-image --name PZCloudManagement --input $taskInput --main-jar PZCloudManagement.jar --main-class pzcloud.PzCloudApp --dest $taskPortable --app-version 1.0.0 --vendor 'Santiago Martin' --description 'Partidas y respaldos de Project Zomboid en Google Drive' --add-modules 'java.base,java.desktop,java.net.http,jdk.httpserver,jdk.crypto.ec,java.logging'
    if ($LASTEXITCODE -ne 0) { throw 'Fallo el paquete portable.' }
    $taskApp = Join-Path $taskPortable 'PZCloudManagement'
    Copy-Item -LiteralPath (Join-Path $taskRepo 'README.md') -Destination $taskApp
    Copy-Item -LiteralPath (Join-Path $taskRepo 'docs') -Destination $taskApp -Recurse
    Write-Host "Aplicacion portable con Java incluido: $taskApp"
}
