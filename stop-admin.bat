@echo off
echo ========================================
echo Stopping Spring Boot Admin Server
echo ========================================
echo.

echo Searching for Admin Server process (port 8080)...

for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do (
    set PID=%%a
)

if defined PID (
    echo Found process: PID %PID%
    echo Stopping...
    taskkill /F /PID %PID%
    echo.
    echo Admin Server stopped successfully.
) else (
    echo Admin Server is not running.
)

echo ========================================
pause
