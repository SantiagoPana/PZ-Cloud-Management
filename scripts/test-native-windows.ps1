$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
$taskApp = Join-Path $taskRepo 'build/portable/KnoxNet'
$taskProbe = Join-Path $taskRepo 'build/native-runtime-check'
if (Test-Path -LiteralPath $taskProbe) { Remove-Item -LiteralPath $taskProbe -Recurse -Force }
New-Item -ItemType Directory -Path $taskProbe | Out-Null
$taskOriginalHash = (Get-FileHash -LiteralPath (Join-Path $taskApp 'app/PZCloudManagement.jar') -Algorithm SHA256).Hash
Copy-Item -LiteralPath $taskApp -Destination $taskProbe -Recurse
$taskCopy = Join-Path $taskProbe 'KnoxNet'
$taskResource = Join-Path $taskProbe 'resources/auth'
New-Item -ItemType Directory -Force -Path $taskResource | Out-Null
# Disposable fixture only. It is never sent to Google or added to the distributed app.
$taskFixture = @{ installed = @{ client_id = 'native-runtime-test.apps.googleusercontent.com'; client_secret = 'public-desktop-fixture' } } | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText((Join-Path $taskResource 'google-client.json'), $taskFixture, [Text.UTF8Encoding]::new($false))
& jar --update --file (Join-Path $taskCopy 'app/PZCloudManagement.jar') -C (Join-Path $taskProbe 'resources') auth/google-client.json
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the disposable native runtime test.' }
$taskOutput = Join-Path $taskProbe 'installation.json'
$taskProcess = Start-Process -FilePath (Join-Path $taskCopy 'KnoxNet.exe') -ArgumentList @('diagnose', ('"{0}"' -f $taskOutput)) -PassThru
if (-not $taskProcess.WaitForExit(45000)) { $taskProcess.Kill(); throw 'Native portable did not finish the installation check.' }
if (-not (Test-Path -LiteralPath $taskOutput)) { throw 'Native portable did not create its diagnostic report.' }
$taskCheck = Get-Content -LiteralPath $taskOutput -Raw | ConvertFrom-Json
if ($taskCheck.applicationName -ne 'Knox Net' -or $taskCheck.description -ne 'PZ Cloud Management' -or $taskCheck.version -ne '2.3.4') { throw 'The packaged app has incorrect branding or version.' }
if (-not $taskCheck.googleIncluded -or $taskCheck.freshCloudMode -ne 'drive' -or $taskCheck.personalSessionCreated) { throw 'Native Windows portable could not read its bundled Google fixture.' }
if ((Get-FileHash -LiteralPath (Join-Path $taskApp 'app/PZCloudManagement.jar') -Algorithm SHA256).Hash -ne $taskOriginalHash) { throw 'The native test modified the distributable app.' }
$taskStatusPath = Join-Path $taskApp 'GOOGLE_AUTH_STATUS.json'
$taskStatus = Get-Content -LiteralPath $taskStatusPath -Raw | ConvertFrom-Json
$taskStatus | Add-Member -NotePropertyName 'nativeWindowsRuntimeTested' -NotePropertyValue $true -Force
$taskStatus | ConvertTo-Json | Set-Content -LiteralPath $taskStatusPath -Encoding utf8
Write-Host 'Native Windows portable verified: EXE, bundled Java runtime, JAR resource and fresh Google mode; no Google account accessed.'
