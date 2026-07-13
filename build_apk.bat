@echo off
setlocal EnableExtensions
chcp 65001 >nul

cd /d "%~dp0"

set "GRADLE_BAT=%USERPROFILE%\.gradle\wrapper\dists\gradle-9.0.0-bin\d6wjpkvcgsg3oed0qlfss3wgl\gradle-9.0.0\bin\gradle.bat"

if not exist "%GRADLE_BAT%" (
  where gradle >nul 2>nul
  if errorlevel 1 (
    echo Gradle not found.
    echo Install Gradle or update GRADLE_BAT in this file.
    pause
    exit /b 1
  )
  set "GRADLE_BAT=gradle"
)

"%GRADLE_BAT%" assembleDebug

echo.
echo APK output: app\build\outputs\apk\debug\app-debug.apk
pause
