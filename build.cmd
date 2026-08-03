@echo off
REM InvasionCore build script.
REM Paper 26.2 API is compiled for Java 25, so JDK 25 must be used.
REM Maven and the JDK are both portable installs under C:\tools (PATH is untouched).
REM NOTE: keep this file ASCII-only - cmd.exe reads it in the OEM codepage.

set "JAVA_HOME=C:\tools\jdk-25.0.4+7"
set "MVN=C:\tools\apache-maven-3.9.16\bin\mvn.cmd"

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [ERROR] JDK 25 not found at %JAVA_HOME%
    exit /b 1
)
if not exist "%MVN%" (
    echo [ERROR] Maven not found at %MVN%
    exit /b 1
)

pushd "%~dp0"
call "%MVN%" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
