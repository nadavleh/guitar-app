@echo off
REM ============================================================
REM  Guitar app launcher
REM  Double-click to:
REM    1. Start the Pixel_7 emulator (with sound) if not running
REM    2. Rebuild + install the latest debug APK (incremental)
REM    3. Launch the app
REM ============================================================

setlocal

set "ANDROID_HOME=C:\Users\Nadav\AppData\Local\Android\Sdk"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\emulator;%PATH%"

cd /d "%~dp0"

echo === Checking emulator ===
adb devices | findstr emulator-5554 >nul
if errorlevel 1 (
    echo Emulator not running. Starting Pixel_7 with audio...
    start "" "%ANDROID_HOME%\emulator\emulator.exe" -avd Pixel_7 -audio winaudio -no-snapshot-load -no-snapshot-save -no-metrics
    echo Waiting for device to come online...
    adb wait-for-device
    echo Waiting for boot to complete...
    :waitloop
    for /f "delims=" %%i in ('adb shell getprop sys.boot_completed 2^>nul') do set "boot=%%i"
    if not "%boot%"=="1" (
        timeout /t 2 /nobreak >nul
        goto waitloop
    )
    echo Emulator booted.
) else (
    echo Emulator already running.
    adb shell input keyevent KEYCODE_WAKEUP >nul
)

echo.
echo === Building and installing app ===
call gradlew.bat :app:installDebug --console=plain
if errorlevel 1 (
    echo.
    echo BUILD FAILED. See output above.
    pause
    exit /b 1
)

echo.
echo === Launching app ===
adb shell am force-stop app.guitar >nul
adb shell am start -n app.guitar/app.guitar.app.MainActivity

echo.
echo App is now running on the emulator. Have fun.
echo (This window can be closed; the emulator and app keep running.)
timeout /t 3 /nobreak >nul
exit /b 0
