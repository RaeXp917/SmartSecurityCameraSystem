package org.example.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * RecognitionLog is a JavaFX-friendly view model of a recognition event
 * joined with the user's display info. Backed by the `recognition_log` table.
 */
public class RecognitionLog {
    private final SimpleStringProperty name;
    private final SimpleStringProperty role;
    private final SimpleStringProperty timestamp;
    private final SimpleDoubleProperty confidence;

    public RecognitionLog(String name, String role, String timestamp, Double confidence) {
        this.name = new SimpleStringProperty(name);
        this.role = new SimpleStringProperty(role);
        this.timestamp = new SimpleStringProperty(timestamp);
        // Format confidence to two decimal places for display
        this.confidence = new SimpleDoubleProperty(Math.round(confidence * 100.0) / 100.0);
    }

    // --- Getters required for JavaFX PropertyValueFactory ---
    public String getName() { return name.get(); }
    public String getRole() { return role.get(); }
    public String getTimestamp() { return timestamp.get(); }
    public double getConfidence() { return confidence.get(); }
}


