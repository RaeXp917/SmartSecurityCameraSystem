@echo off
echo ========================================
echo Smart Security Camera Setup Script
echo ========================================
echo.

echo Checking prerequisites...
echo.

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 21 or higher from: https://adoptium.net/
    echo.
    echo After installation, restart your command prompt and run this script again.
    pause
    exit /b 1
)

REM Check if Gradle is available
gradle -version >nul 2>&1
if %errorlevel% neq 0 (
    echo WARNING: Gradle not found in PATH, using Gradle wrapper...
    echo.
)

echo Java version:
java -version
echo.

echo ========================================
echo Setting up the project...
echo ========================================
echo.

REM Create necessary directories
if not exist "training-data" mkdir training-data
if not exist "recordings" mkdir recordings
if not exist "logs" mkdir logs

echo Created directories:
echo - training-data/
echo - recordings/
echo - logs/
echo.

REM Copy config template if config.properties doesn't exist
if not exist "config.properties" (
    if exist "config.properties.template" (
        copy "config.properties.template" "config.properties"
        echo Created config.properties from template
        echo Please edit config.properties with your settings
    ) else (
        echo WARNING: config.properties.template not found
    )
) else (
    echo config.properties already exists
)
echo.

echo ========================================
echo Database Setup
echo ========================================
echo.
echo Please ensure you have PostgreSQL installed and running
echo Set the following environment variables:
echo.
echo For Command Prompt:
echo set DB_HOST=localhost
echo set DB_PORT=5432
echo set DB_NAME=security_camera_db
echo set DB_USER=your_username
echo set DB_PASSWORD=your_password
echo.
echo For PowerShell:
echo $env:DB_HOST="localhost"
echo $env:DB_PORT="5432"
echo $env:DB_NAME="security_camera_db"
echo $env:DB_USER="your_username"
echo $env:DB_PASSWORD="your_password"
echo.

echo ========================================
echo Building the project...
echo ========================================
echo.

REM Build the project
call gradlew.bat build
if %errorlevel% neq 0 (
    echo ERROR: Build failed
    echo.
    echo Common issues:
    echo - Make sure Java 21+ is installed and in PATH
    echo - Check if you have sufficient disk space
    echo - Verify internet connection for dependencies
    pause
    exit /b 1
)

echo.
echo ========================================
echo Setup Complete!
echo ========================================
echo.
echo Next steps:
echo 1. Configure your database connection using the environment variables above
echo 2. Add training photos to training-data/ directory
echo 3. Run the application: gradlew.bat run
echo 4. Access web interface at: http://localhost:8080
echo.
echo For more information, see README.md
echo.
echo ========================================
echo Security Reminder
echo ========================================
echo.
echo Remember to:
echo - Never commit personal photos to git
echo - Use environment variables for database credentials
echo - Keep your training data private
echo - Regularly backup your data
echo.
pause
