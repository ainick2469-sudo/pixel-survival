@echo off
setlocal

set "REPO_DIR=%~dp0.."
set "TOOLS_DIR=%REPO_DIR%\..\pixel-survival-tools"
set "JAVA_HOME=%TOOLS_DIR%\jdk-21.0.10+7"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Pixel Survival launcher error: Java 21 was not found at:
  echo   %JAVA_HOME%
  pause
  exit /b 1
)

if not exist "%REPO_DIR%\gradlew.bat" (
  echo Pixel Survival launcher error: gradlew.bat was not found at:
  echo   %REPO_DIR%
  pause
  exit /b 1
)

cd /d "%REPO_DIR%"
call gradlew.bat run

if errorlevel 1 (
  echo.
  echo Pixel Survival failed to launch. See output above.
  pause
  exit /b 1
)

endlocal
