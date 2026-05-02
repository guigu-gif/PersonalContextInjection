@echo off
echo ========================================
echo Starting Spring Boot Admin Server
echo ========================================
echo.

cd admin-server

echo [1/2] Checking dependencies...
call mvn dependency:resolve -q

echo [2/2] Starting Admin Server...
echo.
echo After startup, browser will open automatically
echo Monitor URL: http://localhost:8080
echo.
echo Press Ctrl+C to stop the server
echo ========================================
echo.

start "" http://localhost:8080

call mvn spring-boot:run
