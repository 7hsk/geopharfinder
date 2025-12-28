@echo off
setlocal EnableDelayedExpansion

:: ==============================================================================
:: GeoPharFinder - Cleanup Script for Academic Submission
:: ==============================================================================
:: This script removes all local cache, logs, and build artifacts
:: Keeps only source code and documentation for clean submission
:: ==============================================================================

title GeoPharFinder - Cleanup for Submission

echo.
echo ========================================================================
echo   GeoPharFinder - Project Cleanup for Submission
echo ========================================================================
echo.
echo   This will DELETE the following folders/files:
echo.
echo   [X] cache/              - Your cached pharmacy data and map tiles
echo   [X] logs/               - Application log files
echo   [X] target/             - Compiled code and JAR files
echo   [X] geopharfinder.db    - Local database with your searches
echo   [X] .idea/              - IntelliJ IDEA project files (if exists)
echo.
echo   WARNING: This will remove your personal data and usage history!
echo.
echo ========================================================================
echo.

choice /C YN /M "Do you want to continue"
if errorlevel 2 goto :cancel
if errorlevel 1 goto :cleanup

:cleanup
echo.
echo [1/6] Cleaning cache folder...
if exist cache (
    rd /s /q cache
    echo      - cache/ folder deleted
) else (
    echo      - cache/ folder not found (already clean)
)

echo.
echo [2/6] Cleaning logs folder...
if exist logs (
    rd /s /q logs
    echo      - logs/ folder deleted
) else (
    echo      - logs/ folder not found (already clean)
)

echo.
echo [3/6] Cleaning target folder (build artifacts)...
if exist target (
    rd /s /q target
    echo      - target/ folder deleted
) else (
    echo      - target/ folder not found (already clean)
)

echo.
echo [4/6] Cleaning database file...
if exist geopharfinder.db (
    del /f /q geopharfinder.db
    echo      - geopharfinder.db deleted
) else (
    echo      - geopharfinder.db not found (already clean)
)
if exist pharmalocator.db (
    del /f /q pharmalocator.db
    echo      - pharmalocator.db deleted (old name)
)

echo.
echo [5/6] Cleaning IDE files (.idea folder)...
if exist .idea (
    rd /s /q .idea
    echo      - .idea/ folder deleted
) else (
    echo      - .idea/ folder not found (already clean)
)

echo.
echo [6/6] Cleaning other temporary files...
if exist *.tmp del /f /q *.tmp 2>nul
if exist *.temp del /f /q *.temp 2>nul
if exist *.log del /f /q *.log 2>nul
echo      - Temporary files cleaned

echo.
echo ========================================================================
echo   Cleanup Summary
echo ========================================================================
echo.

:: Calculate folder sizes
set "totalSize=0"

for /f "tokens=3" %%a in ('dir /s /-c src ^| find "bytes"') do set srcSize=%%a
for /f "tokens=3" %%a in ('dir /-c pom.xml ^| find "bytes"') do set pomSize=%%a

echo   Source Code (src/):        ~5 MB
echo   Maven Config (pom.xml):    ~10 KB
echo   Documentation (*.md):      ~50 KB
echo   Scripts (*.bat):           ~10 KB
echo.
echo   Total project size:        ~5-8 MB
echo.
echo ========================================================================
echo.
echo   [SUCCESS] Project cleaned successfully!
echo.
echo   Your project is now ready for submission.
echo.
echo   Next steps:
echo   1. Verify cleanup: dir /w
echo   2. Create ZIP: Right-click folder ^> Send to ^> Compressed folder
echo   3. Rename ZIP: GeoPharFinder_TeamExodia_Submission.zip
echo   4. Submit to professor
echo.
echo ========================================================================
echo.

:: Display remaining files
echo   Remaining files and folders:
echo.
dir /b /a-h

echo.
echo   Detailed view:
echo.
dir | find /v "bytes free"

echo.
echo ========================================================================
echo.
echo   Press any key to test build (optional)...
pause >nul

echo.
echo   Testing Maven build...
echo.
mvn clean compile

if errorlevel 1 (
    echo.
    echo   [WARNING] Build failed! Please check your source code.
    echo.
) else (
    echo.
    echo   [SUCCESS] Build successful! Your project is ready to submit.
    echo.
)

echo.
echo   Press any key to exit...
pause >nul
goto :end

:cancel
echo.
echo   Cleanup cancelled. No changes were made.
echo.
echo   Press any key to exit...
pause >nul
goto :end

:end
endlocal
exit /b 0

