@echo off
setlocal
REM Build exe installer
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-exe.ps1" -Release
exit /b %ERRORLEVEL%

