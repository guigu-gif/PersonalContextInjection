@echo off
chcp 65001 >nul
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-services.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

if /I not "%~1"=="--no-pause" (
    echo.
    if "%EXIT_CODE%"=="0" (
        echo Start script finished.
    ) else (
        echo Start script failed with exit code %EXIT_CODE%.
    )
    pause
)

exit /b %EXIT_CODE%
