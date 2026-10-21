@echo off
REM Wrapper so setup-server.ps1 runs regardless of the PowerShell execution policy.
REM Usage: scripts\setup-server.cmd [-AcceptEula] [-Port 25565] [-NormalWorld] [-Path <dir>]
REM NOTE: keep this file ASCII-only - cmd.exe reads it in the OEM codepage.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-server.ps1" %*
exit /b %ERRORLEVEL%
