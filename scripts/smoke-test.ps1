# Safe smoke test: uses disposable fake save files only.
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$work = Join-Path $env:TEMP ("PZCloudSmoke-" + [guid]::NewGuid().ToString("N"))
try {
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
        throw "No se encontro javac. Instala JDK 21 y reinicia PowerShell."
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw "No se encontro java. Instala JDK 21 y reinicia PowerShell."
    }

    $save = Join-Path $work "partida-ficticia"
    $backup = Join-Path $work "backups"
    $classes = Join-Path $work "classes"
    $unpacked = Join-Path $work "extracted"

    New-Item -ItemType Directory -Force -Path $save, $backup, $classes | Out-Null
    $originalFile = Join-Path $save "world-data.txt"
    Set-Content -LiteralPath $originalFile -Value "PZ CLOUD TEST - no es una partida real" -Encoding UTF8
    $beforeHash = (Get-FileHash -LiteralPath $originalFile -Algorithm SHA256).Hash

    Write-Host "[1/4] Compilando Java..."
    & javac -d $classes (Join-Path $repo "src\main\java\pzcloud\LocalBackupEngine.java") (Join-Path $repo "src\main\java\pzcloud\BackupCli.java")
    if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion Java." }

    Write-Host "[2/4] Creando backup de partida ficticia..."
    & java -cp $classes pzcloud.BackupCli $save $backup
    if ($LASTEXITCODE -ne 0) { throw "Fallo la generacion del ZIP." }

    $archives = @(Get-ChildItem -LiteralPath $backup -Filter "*.zip" -File)
    if ($archives.Count -ne 1) { throw "Esperaba exactamente un ZIP; encontre $($archives.Count)." }

    Write-Host "[3/4] Extrayendo ZIP y comparando integridad..."
    Expand-Archive -LiteralPath $archives[0].FullName -DestinationPath $unpacked -Force
    $restoredFile = Join-Path $unpacked "world-data.txt"
    if (-not (Test-Path -LiteralPath $restoredFile)) { throw "El archivo no esta dentro del ZIP." }
    $afterHash = (Get-FileHash -LiteralPath $restoredFile -Algorithm SHA256).Hash
    $sourceHash = (Get-FileHash -LiteralPath $originalFile -Algorithm SHA256).Hash
    if ($beforeHash -ne $afterHash -or $sourceHash -ne $beforeHash) {
        throw "ERROR: diferencia de contenido o se modifico el archivo de origen."
    }

    Write-Host "[4/4] OK. ZIP creado, extraido y verificado. Archivo original intacto." -ForegroundColor Green
    Write-Host "Este test NO instala el mod ni prueba la carga dentro de Project Zomboid."
} finally {
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
}
