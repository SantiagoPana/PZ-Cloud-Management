# Inspect the actual portable; do not modify, whitelist, or suppress antivirus.
# Defender is optional on GitHub runners: unsupported scans are labelled "not-run", never "passed".
param([string]$AppDirectory = "")
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (-not $AppDirectory) { $AppDirectory = Join-Path $repo 'build/portable/KnoxNet' }
$app = (Resolve-Path -LiteralPath $AppDirectory).Path
$exe = Join-Path $app 'KnoxNet.exe'
$jar = Join-Path $app 'app/PZCloudManagement.jar'
$statusFile = Join-Path $app 'SECURITY_STATUS.json'
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw "Missing KnoxNet.exe" }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Missing application JAR" }
if (-not (Test-Path -LiteralPath (Join-Path $app 'runtime') -PathType Container)) { throw "Missing private Java runtime" }

$signature = Get-AuthenticodeSignature -LiteralPath $exe
if ($signature.Status -notin @('Valid', 'NotSigned')) {
    throw "Unexpected or invalid executable signature: $($signature.Status)"
}
$scan = 'not-run'
$scanReason = 'Microsoft Defender unavailable on this runner.'
if ((Get-Command Get-MpComputerStatus -ErrorAction SilentlyContinue) -and (Get-Command Start-MpScan -ErrorAction SilentlyContinue)) {
    try {
        $defender = Get-MpComputerStatus -ErrorAction Stop
        if ($defender.AMServiceEnabled -and $defender.AntivirusEnabled) {
            # This is a genuine AV scan, not a substitute for Avast analysis or runtime verification.
            Start-MpScan -ScanType CustomScan -ScanPath $app -ErrorAction Stop
            if (Get-Command Get-MpThreatDetection -ErrorAction SilentlyContinue) {
                $detections = @(Get-MpThreatDetection -ErrorAction Stop)
                $related = @($detections | Where-Object {
                    $_.Resources -and (($_.Resources -join ' ') -like "*$app*")
                })
                if ($related.Count -gt 0) {
                    throw "Defender reported $($related.Count) detection(s) associated with the portable."
                }
                $scan = 'passed'
                $scanReason = 'Defender custom scan completed; no portable detections reported.'
            } else {
                $scan = 'completed-unverified'
                $scanReason = 'Defender scan completed, but the threat-detection query was unavailable.'
            }
        } else {
            $scanReason = 'Microsoft Defender AV service is disabled on this runner.'
        }
    } catch {
        # A scan error or detection must not accidentally be advertised as success.
        throw "Defender scan could not be verified: $($_.Exception.Message)"
    }
}
$report = [ordered]@{
    application = 'Knox Net'
    version = '2.3.4'
    exeSha256 = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
    jarSha256 = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    authenticode = [string]$signature.Status
    microsoftDefender = $scan
    defenderDetails = $scanReason
    avastTested = $false
    liveBehaviorTested = $false
}
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statusFile -Encoding utf8
Write-Host "Security inspection complete: Authenticode=$($report.authenticode), Defender=$scan"
if ($scan -ne 'passed') { Write-Warning "The Windows Defender scan was NOT a verified clean pass: $scanReason" }
Write-Host "SHA-256 records written to SECURITY_STATUS.json; no user credentials, sessions or backups were accessed."
