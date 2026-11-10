@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1"
if errorlevel 1 (
  pause
  exit /b 1
)
where javaw.exe >nul 2>nul
if errorlevel 1 (
  echo Instala JDK 21: winget install Microsoft.OpenJDK.21
  echo Despues abri otra ventana y volve a ejecutar este archivo.
  pause
  exit /b 1
)
start "" javaw.exe -jar "%~dp0build\PZCloudManagement.jar"
