@echo off
setlocal

echo ========================================
echo   PharmaLocator - Starting Application
echo ========================================
cd /d "%~dp0"

REM Check if Java is installed
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 17 or higher
    pause
    exit /b 1
)

REM Check if JAR exists
if not exist "target\geopharfinder-1.0.0.jar" (
    echo JAR file not found. Building project...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo ERROR: Build failed
        pause
        exit /b 1
    )
)

echo Starting PharmaLocator...
echo.
echo NOTE: This window will close automatically when you exit the app.
echo       Or press Ctrl+C here to force quit.
echo.

REM Run Java directly (attached to this terminal)
REM This ensures the process dies when terminal closes
java -jar target\geopharfinder-1.0.0.jar

REM If we reach here, the app closed normally
echo.
echo Application closed.

REM Extra safety: Kill any lingering Java processes
taskkill /F /FI "IMAGENAME eq java.exe" /FI "WINDOWTITLE eq *PharmaLocator*" >nul 2>&1

exit /b 0

