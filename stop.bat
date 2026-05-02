@echo off
chcp 65001 >nul
setlocal

node "%~dp0scripts\stop.js"
set "EXIT_CODE=%ERRORLEVEL%"

if /I not "%~1"=="--no-pause" (
    echo.
    if "%EXIT_CODE%"=="0" (
        echo Stop script finished.
    ) else (
        echo Stop script failed with exit code %EXIT_CODE%.
    )
    pause
)

exit /b %EXIT_CODE%
