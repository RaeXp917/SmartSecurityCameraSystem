# Smart Security Camera System

A Java-based intelligent security camera system with face recognition, body detection, and web-based monitoring capabilities.

### ⚠️ Disclaimer

This project is provided “as is” without any warranties of any kind, either express or implied. The authors and contributors are not responsible for any misuse, data loss, security breaches, or legal issues that may arise from the use of this system.

By using this software, you agree that:
- You are solely responsible for configuring and securing your own deployment.
- You are responsible for complying with all applicable privacy, surveillance, and data protection laws in your country.
- The maintainers of this repository do not provide any guarantees regarding data safety, uptime, or suitability for production environments.

## Features

-   **Real-time Face Recognition**: Identify known individuals using LBPH algorithm
-   **Body Detection**: Detect human bodies in camera feeds
-   **Web Interface**: Monitor your camera feeds from any device via a web browser
-   **Video Recording**: Automatic recording of security events
-   **User Management**: Add/remove authorized users with role-based access
-   **Height Profiling**: Advanced biometric identification using height analysis
-   **Multi-camera Support**: Webcam and IP camera support
-   **Database Logging**: PostgreSQL-based event logging and user management

## Technology Stack

-   **Backend**: Java 21 + JavaFX + OpenCV
-   **Web Server**: Ktor (Kotlin)
-   **Database**: PostgreSQL
-   **Face Recognition**: OpenCV LBPH
-   **Build System**: Gradle
-   **Computer Vision**: JavaCV + OpenCV

## Prerequisites

-   Java 21 or higher
-   PostgreSQL database
-   Webcam or IP camera
-   Gradle 8.0+

## Quick Start

### 1. Set Up Database

Create a PostgreSQL database and set environment variables:

**Windows (Command Prompt):**

    set DB_HOST=localhost
    set DB_PORT=5432
    set DB_NAME=security_camera_db
    set DB_USER=your_username
    set DB_PASSWORD=your_password

**Linux:**

    export DB_HOST=localhost
    export DB_PORT=5432
    export DB_NAME=security_camera_db
    export DB_USER=your_username
    export DB_PASSWORD=your_password

### 2. Build and Run

**Windows:**

    gradlew.bat build
    gradlew.bat run

**Linux:**

    ./gradlew build
    ./gradlew run

### 3. Add Training Data

1.  Navigate to the `training-data/` directory.
2.  Create role-based folders (e.g., `Owner/`, `Family/`, `Employee`).
3.  Add person folders with their photos (e.g., `Owner/John/photo1.jpg`).
4.  Restart the application to train the model.

### 4. Access Web Interface

Open your browser and navigate to `http://localhost:8080`

## Configuration

### Camera Settings

Edit `config.properties`:

    camera.type=WEBCAM
    ip.camera.url=rtsp://username:password@192.168.1.100:554/stream

### Database Configuration

Set environment variables for database connection:
- `DB_HOST`: Database host (default: `localhost`)
- `DB_PORT`: Database port (default: `5432`)
- `DB_NAME`: Database name (default: `security_camera_db`)
- `DB_USER`: Database username (default: `postgres`)
- `DB_PASSWORD`: Database password

## Security Features

-   **Face Recognition**: LBPH algorithm for reliable identification
-   **Height Profiling**: Additional biometric verification
-   **Event Logging**: Comprehensive audit trail
-   **Role-based Access**: Different permission levels for users
-   **Secure Database**: PostgreSQL with proper authentication

## Web Interface

The system provides a web-based dashboard accessible at `http://localhost:8080`:
- Live camera feed
- Real-time event monitoring
- User management interface
- System status indicators

## Training Data Management

### Adding New Users

1.  Use the application’s UI to add users.
2.  Capture training photos through the camera.
3.  Photos are automatically saved to `training-data/[Role]/[Name]/`.
4.  The system retrains automatically after adding new photos.

### Photo Requirements

-   Clear, front-facing photos
-   Good lighting conditions
-   Multiple angles and expressions
-   Recommended: 10-20 photos per person
-   JPG format preferred

## Recording Features

-   **Automatic Recording**: Records when unknown faces are detected.
-   **Event-based Storage**: Saves recordings to `recordings/` directory.
-   **Configurable Duration**: Adjustable recording length.
-   **Storage Management**: Automatic cleanup of old recordings.

### Access Web Interface

-   **On the same computer:**
    Open your browser and go to: `http://localhost:8080`

-   **From another device on your local network (phone, tablet, laptop, etc.):**
    1.  Open Command Prompt (Windows) and run:

            ipconfig

    2.  Copy your **IPv4 Address** (e.g., `192.168.1.40`).
    3.  In the browser of the other device, go to:

            http://<your-ipv4>:8080

        Example: `http://192.168.1.40:8080`

## License

This project is licensed under the MIT License - see the `LICENSE` file for details.

## Acknowledgments

-   OpenCV community for computer vision libraries
-   JavaCV for Java bindings
-   Ktor team for the web framework
-   PostgreSQL for the database system

---
*Note: This system is designed for personal and small business use. Ensure compliance with local privacy laws and regulations when deploying in production environments.*```
