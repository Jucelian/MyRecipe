@echo off
title ChefMate Server
echo.
echo ========================================
echo        STARTING CHEFMATE SERVER
echo ========================================
echo.

:: Try to find Java from Android Studio if JAVA_HOME isn't set
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    ) else if exist "C:\Program Files\Android\Android Studio\jre" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jre"
    )
)

if "%JAVA_HOME%"=="" (
    echo [ERROR] Java not found. Please open Android Studio or set JAVA_HOME.
    pause
    exit /b
)

echo [INFO] Using Java from: %JAVA_HOME%
echo [INFO] Building and starting server...
echo.

call gradlew.bat :server:run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Server failed to start.
    pause
)
