@echo off
REM Double-click launcher for Chorect Web.
REM Installs dependencies on first run, then starts the Vite dev server and opens
REM your browser. Leave this window open while you use the app; close it to stop.

cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo   Node.js was not found on your PATH.
  echo   Install the LTS build from https://nodejs.org/ and run this file again.
  echo.
  pause
  exit /b 1
)

if not exist node_modules (
  echo.
  echo   First run: installing dependencies ^(this happens only once^)...
  echo.
  call npm install
  if errorlevel 1 (
    echo.
    echo   npm install failed. See the messages above.
    pause
    exit /b 1
  )
)

echo.
echo   Starting Chorect Web - your browser will open at http://localhost:5317
echo   Keep this window open while you use the app. Press Ctrl+C here to stop.
echo.
call npm run dev

pause
