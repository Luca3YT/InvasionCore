@echo off
REM Builds the InvasionCore resource pack from the PNGs in resourcepack\textures.
REM Output: target\InvasionCore-resourcepack.zip
REM CustomModelData values come from server\plugins\InvasionCore\item_model.yml
REM (or the template in src\main\resources when the test server has none).
REM The pack copies the vanilla item definitions from the Minecraft client jar, so the
REM client of the same version must be installed (or pass its jar as the first argument).
REM NOTE: keep this file ASCII-only - cmd.exe reads it in the OEM codepage.

setlocal
set "REPO=%~dp0.."
if exist "%REPO%\build.local.cmd" call "%REPO%\build.local.cmd"
if not defined JAVA_HOME (
    for /d %%D in (
        "%USERPROFILE%\.jdks\openjdk-25*"
        "%USERPROFILE%\.jdks\openjdk-26*"
        "%USERPROFILE%\.jdks\temurin-25*"
        "%USERPROFILE%\.jdks\temurin-26*"
        "C:\tools\jdk-25*"
        "C:\tools\jdk-26*"
        "%ProgramFiles%\Eclipse Adoptium\jdk-25*"
        "%ProgramFiles%\Eclipse Adoptium\jdk-26*"
    ) do if exist "%%~D\bin\java.exe" set "JAVA_HOME=%%~D"
)
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 25+ not found. Set JAVA_HOME in build.local.cmd.
    exit /b 1
)

set "CLIENT_JAR=%~1"
if not defined CLIENT_JAR set "CLIENT_JAR=%APPDATA%\.minecraft\versions\26.2\26.2.jar"
if not exist "%CLIENT_JAR%" (
    echo [ERROR] Minecraft client jar not found: %CLIENT_JAR%
    echo         Start Minecraft 26.2 once from the launcher, or pass the jar path as the first argument.
    exit /b 1
)

"%JAVA_HOME%\bin\java.exe" -Dstdout.encoding=UTF-8 "%REPO%\scripts\resourcepack\BuildResourcePack.java" "%REPO%" "%CLIENT_JAR%" "%REPO%\target\InvasionCore-resourcepack.zip"
exit /b %ERRORLEVEL%
