@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "POWERSHELL_BIN="

where pwsh.exe >nul 2>nul
if not errorlevel 1 set "POWERSHELL_BIN=pwsh.exe"

if not defined POWERSHELL_BIN (
  where powershell.exe >nul 2>nul
  if not errorlevel 1 set "POWERSHELL_BIN=powershell.exe"
)

if not defined POWERSHELL_BIN (
  echo PowerShell is required. 1>&2
  exit /b 1
)

"%POWERSHELL_BIN%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%run_latest_release.ps1" %*
exit /b %errorlevel%
