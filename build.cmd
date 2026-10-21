@echo off
REM InvasionCore build script.
REM Paper 26.2 API is compiled for Java 25, so a JDK 25 or newer must be used.
REM NOTE: keep this file ASCII-only - cmd.exe reads it in the OEM codepage.
REM
REM Toolchain lookup order (PATH is never modified globally):
REM   1. build.local.cmd next to this file (gitignored) - set JAVA_HOME / MVN there
REM   2. JAVA_HOME / MVN already set in the environment
REM   3. Auto-detect: %USERPROFILE%\.jdks, C:\tools, Eclipse Adoptium for the JDK;
REM      PATH, C:\tools, then the Maven bundled with IntelliJ IDEA for Maven

setlocal

if exist "%~dp0build.local.cmd" call "%~dp0build.local.cmd"

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
    ) do if exist "%%~D\bin\javac.exe" set "JAVA_HOME=%%~D"
)

if not defined MVN (
    for /f "delims=" %%M in ('where mvn.cmd 2^>nul') do if not defined MVN set "MVN=%%M"
)
if not defined MVN (
    for /d %%D in ("C:\tools\apache-maven-*") do if exist "%%D\bin\mvn.cmd" set "MVN=%%D\bin\mvn.cmd"
)
if not defined MVN (
    for /d %%D in ("%ProgramFiles%\JetBrains\IntelliJ IDEA*") do (
        if exist "%%D\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" set "MVN=%%D\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
        if exist "%%D\plugins\maven\lib\maven3\bin\mvn.cmd" set "MVN=%%D\plugins\maven\lib\maven3\bin\mvn.cmd"
    )
)

if not defined JAVA_HOME (
    echo [ERROR] JDK 25+ not found. Set JAVA_HOME in build.local.cmd.
    exit /b 1
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [ERROR] JAVA_HOME does not point to a JDK: %JAVA_HOME%
    exit /b 1
)
if not defined MVN (
    echo [ERROR] Maven not found. Set MVN in build.local.cmd.
    exit /b 1
)

echo [build] JAVA_HOME=%JAVA_HOME%
echo [build] MVN=%MVN%

pushd "%~dp0"
call "%MVN%" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
