package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.AppConfig;

/**
 * SettingsWindow allows the user to choose between the default webcam
 * and an IP camera stream and to configure the IP camera URL.
 */
public class SettingsWindow {

    private final AppConfig appConfig;
    private final Runnable onSave;

    public SettingsWindow(AppConfig appConfig, Runnable onSave) {
        this.appConfig = appConfig;
        this.onSave = onSave;
    }

    public void show(Stage owner) {
        Stage settingsStage = new Stage();
        settingsStage.setTitle("Application Settings");
        settingsStage.initOwner(owner);
        settingsStage.initModality(Modality.APPLICATION_MODAL);

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));

        Label cameraSourceLabel = new Label("Camera Source:");
        ToggleGroup cameraSourceGroup = new ToggleGroup();

        RadioButton webcamRadio = new RadioButton("Default Webcam (Index 0)");
        webcamRadio.setToggleGroup(cameraSourceGroup);
        webcamRadio.setUserData("WEBCAM");

        RadioButton ipCameraRadio = new RadioButton("IP Camera (RTSP/HTTP Stream)");
        ipCameraRadio.setToggleGroup(cameraSourceGroup);
        ipCameraRadio.setUserData("IP_CAMERA");

        TextField ipCameraUrlField = new TextField(appConfig.getIpCameraUrl());
        ipCameraUrlField.setPromptText("e.g., rtsp://username:password@192.168.1.100:554/stream");
        ipCameraUrlField.setPrefWidth(300);

        if (appConfig.getCameraType().equals("WEBCAM")) {
            webcamRadio.setSelected(true);
            ipCameraUrlField.setDisable(true);
        } else {
            ipCameraRadio.setSelected(true);
            ipCameraUrlField.setDisable(false);
        }

        cameraSourceGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                ipCameraUrlField.setDisable(!newToggle.getUserData().equals("IP_CAMERA"));
            }
        });

        HBox cameraRadios = new HBox(10, webcamRadio, ipCameraRadio);
        cameraRadios.setAlignment(Pos.CENTER_LEFT);

        HBox ipUrlBox = new HBox(10, new Label("URL:"), ipCameraUrlField);
        ipUrlBox.setAlignment(Pos.CENTER_LEFT);

        Button saveButton = new Button("Save Settings");
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        saveButton.setOnAction(e -> {
            String selectedCameraType = (String) cameraSourceGroup.getSelectedToggle().getUserData();
            appConfig.setCameraType(selectedCameraType);
            appConfig.setIpCameraUrl(ipCameraUrlField.getText().trim());
            appConfig.saveProperties();
            if (onSave != null) onSave.run();
            settingsStage.close();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> settingsStage.close());

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(cameraSourceLabel, cameraRadios, ipUrlBox, new Separator(), buttonBox);
        layout.setAlignment(Pos.TOP_LEFT);

        Scene scene = new Scene(layout, 450, 250);
        settingsStage.setScene(scene);
        settingsStage.showAndWait();
    }
}


