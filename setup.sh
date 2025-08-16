#!/bin/bash

echo "========================================"
echo "Smart Security Camera Setup Script (Linux)"
echo "========================================"
echo

echo "Checking prerequisites..."
echo

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 21 or higher:"
    echo "  Ubuntu/Debian: sudo apt install openjdk-21-jdk"
    echo "  CentOS/RHEL: sudo yum install java-21-openjdk-devel"
    echo "  Or download from: https://adoptium.net/"
    exit 1
fi

# Check if Gradle is available
if ! command -v gradle &> /dev/null; then
    echo "WARNING: Gradle not found in PATH, using Gradle wrapper..."
    echo
fi

echo "Java version:"
java -version
echo

echo "========================================"
echo "Setting up the project..."
echo "========================================"
echo

# Create necessary directories
mkdir -p training-data
mkdir -p recordings
mkdir -p logs

echo "Created directories:"
echo "- training-data/"
echo "- recordings/"
echo "- logs/"
echo

# Copy config template if config.properties doesn't exist
if [ ! -f "config.properties" ]; then
    if [ -f "config.properties.template" ]; then
        cp "config.properties.template" "config.properties"
        echo "Created config.properties from template"
        echo "Please edit config.properties with your settings"
    else
        echo "WARNING: config.properties.template not found"
    fi
else
    echo "config.properties already exists"
fi
echo

echo "========================================"
echo "Database Setup"
echo "========================================"
echo
echo "Please ensure you have PostgreSQL installed and running"
echo "Install PostgreSQL if needed:"
echo "  Ubuntu/Debian: sudo apt install postgresql postgresql-contrib"
echo "  CentOS/RHEL: sudo yum install postgresql postgresql-server"
echo
echo "Set the following environment variables:"
echo
echo "export DB_HOST=localhost"
echo "export DB_PORT=5432"
echo "export DB_NAME=security_camera_db"
echo "export DB_USER=your_username"
echo "export DB_PASSWORD=your_password"
echo

echo "========================================"
echo "Building the project..."
echo "========================================"
echo

# Make gradlew executable
chmod +x gradlew

# Build the project
./gradlew build
if [ $? -ne 0 ]; then
    echo "ERROR: Build failed"
    echo
    echo "Common issues:"
    echo "- Make sure Java 21+ is installed and in PATH"
    echo "- Check if you have sufficient disk space"
    echo "- Verify internet connection for dependencies"
    echo "- Make sure gradlew is executable: chmod +x gradlew"
    exit 1
fi

echo
echo "========================================"
echo "Setup Complete!"
echo "========================================"
echo
echo "Next steps:"
echo "1. Configure your database connection using the environment variables above"
echo "2. Add training photos to training-data/ directory"
echo "3. Run the application: ./gradlew run"
echo "4. Access web interface at: http://localhost:8080"
echo
echo "For more information, see README.md"
echo
echo "========================================"
echo "Security Reminder"
echo "========================================"
echo
echo "Remember to:"
echo "- Never commit personal photos to git"
echo "- Use environment variables for database credentials"
echo "- Keep your training data private"
echo "- Regularly backup your data"
echo
