package org.example;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class AppConfig {

    private static final String CONFIG_FILE = "config.properties";
    private static final String DEFAULT_CAMERA_TYPE = "WEBCAM"; // Options: WEBCAM, IP_CAMERA
    private static final String DEFAULT_IP_CAMERA_URL = "";

    private Properties properties;

    public AppConfig() {
        properties = new Properties();
        loadProperties();
    }

    private void loadProperties() {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            properties.load(fis);
            System.out.println("Configuration loaded from " + CONFIG_FILE);
        } catch (IOException e) {
            System.out.println("No existing config.properties found, loading defaults.");
            setDefaults();
            saveProperties(); // Save defaults if file didn't exist
        }
    }

    public void saveProperties() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "Smart Security Camera Configuration");
            System.out.println("Configuration saved to " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error saving configuration: " + e.getMessage());
        }
    }

    private void setDefaults() {
        properties.setProperty("camera.type", DEFAULT_CAMERA_TYPE);
        properties.setProperty("ip.camera.url", DEFAULT_IP_CAMERA_URL);
    }

    public String getCameraType() {
        return properties.getProperty("camera.type", DEFAULT_CAMERA_TYPE);
    }

    public void setCameraType(String cameraType) {
        properties.setProperty("camera.type", cameraType);
    }

    public String getIpCameraUrl() {
        return properties.getProperty("ip.camera.url", DEFAULT_IP_CAMERA_URL);
    }

    public void setIpCameraUrl(String ipCameraUrl) {
        properties.setProperty("ip.camera.url", ipCameraUrl);
    }
}