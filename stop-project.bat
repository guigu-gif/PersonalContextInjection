@echo off
chcp 65001 >nul
setlocal

call "%~dp0stop.bat" --no-pause
set "EXIT_CODE=%ERRORLEVEL%"

if /I not "%~1"=="--no-pause" (
    echo.
    if "%EXIT_CODE%"=="0" (
        echo Stop project script finished.
    ) else (
        echo Stop project script failed with exit code %EXIT_CODE%.
    )
    pause
)

exit /b %EXIT_CODE%
