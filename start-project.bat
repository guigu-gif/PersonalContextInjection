@echo off
chcp 65001 >nul
setlocal

call "%~dp0start.bat" --no-pause
set "EXIT_CODE=%ERRORLEVEL%"

if /I not "%~1"=="--no-pause" (
    echo.
    if "%EXIT_CODE%"=="0" (
        echo Start project script finished.
    ) else (
        echo Start project script failed with exit code %EXIT_CODE%.
    )
    pause
)

exit /b %EXIT_CODE%
