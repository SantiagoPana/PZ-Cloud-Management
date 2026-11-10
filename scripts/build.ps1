param([switch]$Package, [string]$GoogleClientFile = $env:PZCLOUD_GOOGLE_CLIENT_FILE, [switch]$RequireGoogleLogin)
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
$taskBuild = Join-Path $taskRepo 'build'
$taskClasses = Join-Path $taskBuild 'classes'
$taskResources = Join-Path $taskBuild 'resources'
$taskRuntimeClasses = Join-Path $taskBuild 'runtime-classes'
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Instala JDK 21: winget install Microsoft.OpenJDK.21. Luego abri otra terminal.' }
if (Test-Path $taskClasses) { Remove-Item -LiteralPath $taskClasses -Recurse -Force }
if (Test-Path $taskResources) { Remove-Item -LiteralPath $taskResources -Recurse -Force }
if (Test-Path $taskRuntimeClasses) { Remove-Item -LiteralPath $taskRuntimeClasses -Recurse -Force }
New-Item -ItemType Directory -Force -Path $taskClasses, $taskResources, $taskRuntimeClasses | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'src/main/java/pzcloud') -Filter '*.java' -File | ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -d $taskClasses @taskSources
if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion.' }
if (-not $GoogleClientFile) {
    $taskDefaultClient = Join-Path $taskRepo 'release/google-client.json'
    if (Test-Path -LiteralPath $taskDefaultClient) { $GoogleClientFile = $taskDefaultClient }
}
$taskReport = Join-Path $taskBuild 'google-auth-status.json'
$taskPublish = @('-cp', $taskClasses, 'pzcloud.PublisherConfig', $taskResources, $taskReport)
if ($GoogleClientFile) { $taskPublish += $GoogleClientFile }
if ($RequireGoogleLogin) { $taskPublish += '--required' }
& java @taskPublish
if ($LASTEXITCODE -ne 0) { throw 'Fallo la configuracion de Google del instalable.' }
$taskJar = Join-Path $taskBuild 'PZCloudManagement.jar'
$taskRuntimePackage = Join-Path $taskRuntimeClasses 'pzcloud'
New-Item -ItemType Directory -Force -Path $taskRuntimePackage | Out-Null
Get-ChildItem -LiteralPath (Join-Path $taskClasses 'pzcloud') -Filter '*.class' -File | Where-Object { $_.Name -notlike 'PublisherConfig*' } | Copy-Item -Destination $taskRuntimePackage
$taskJarArgs = @('--create', '--file', $taskJar, '--main-class', 'pzcloud.PzCloudApp', '-C', $taskRuntimeClasses, '.', '-C', $taskRepo, 'mod')
if (Test-Path -LiteralPath (Join-Path $taskResources 'auth/google-client.json')) { $taskJarArgs += @('-C', $taskResources, '.') }
& jar @taskJarArgs
if ($LASTEXITCODE -ne 0) { throw 'Fallo la creacion del JAR.' }
Write-Host "JAR listo: $taskJar"
if ($Package) {
    $taskInput = Join-Path $taskBuild 'package-input'
    $taskPortable = Join-Path $taskBuild 'portable'
    New-Item -ItemType Directory -Force -Path $taskInput | Out-Null
    Copy-Item -LiteralPath $taskJar -Destination $taskInput -Force
    if (Test-Path $taskPortable) { Remove-Item -LiteralPath $taskPortable -Recurse -Force }
    & jpackage --type app-image --name PZCloudManagement --input $taskInput --main-jar PZCloudManagement.jar --main-class pzcloud.PzCloudApp --dest $taskPortable --app-version 2.3.1 --vendor 'Santiago Martin' --description 'Partidas y respaldos de Project Zomboid en Google Drive' --add-modules 'java.base,java.desktop,java.net.http,jdk.httpserver,jdk.crypto.ec,java.logging' --jlink-options '--strip-debug --strip-native-commands --no-header-files --no-man-pages --compress=zip-6' --java-options '-Xms8m' --java-options '-Xmx768m' --java-options '-XX:+UseSerialGC' --java-options '-XX:MinHeapFreeRatio=10' --java-options '-XX:MaxHeapFreeRatio=20'
    if ($LASTEXITCODE -ne 0) { throw 'Fallo el paquete portable.' }
    $taskApp = Join-Path $taskPortable 'PZCloudManagement'
    Copy-Item -LiteralPath (Join-Path $taskRepo 'docs/CLIENT_README.md') -Destination (Join-Path $taskApp 'README.md')
    $taskUserDocs = Join-Path $taskApp 'docs'
    New-Item -ItemType Directory -Force -Path $taskUserDocs | Out-Null
    foreach ($taskDoc in @('QUICKSTART.md', 'GOOGLE_DRIVE.md', 'PRIVACY.md')) {
        Copy-Item -LiteralPath (Join-Path $taskRepo "docs/$taskDoc") -Destination $taskUserDocs
    }
    Copy-Item -LiteralPath $taskReport -Destination (Join-Path $taskApp 'GOOGLE_AUTH_STATUS.json')
    Write-Host "Aplicacion portable con Java incluido: $taskApp"
}
