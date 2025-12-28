@echo off
echo ========================================
echo   Building GeoPharFinder with Options
echo ========================================
cd /d "%~dp0"
call mvn clean package -DskipTests
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   Build Successful!
    echo ========================================
    echo.
) else (
    echo.
    echo ========================================
    echo   Build Failed!
    echo ========================================
    pause
    exit /b 1
)
