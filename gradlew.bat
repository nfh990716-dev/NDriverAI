@echo off
setlocal
set GRADLE_VERSION=8.13
set GRADLE_ROOT=%USERPROFILE%\.gradle\noor-wrapper
set GRADLE_HOME_DIR=%GRADLE_ROOT%\gradle-%GRADLE_VERSION%
if not exist "%GRADLE_HOME_DIR%\bin\gradle.bat" (
  if not exist "%GRADLE_ROOT%" mkdir "%GRADLE_ROOT%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip'; Expand-Archive -Force '%GRADLE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip' '%GRADLE_ROOT%'"
)
call "%GRADLE_HOME_DIR%\bin\gradle.bat" %*
