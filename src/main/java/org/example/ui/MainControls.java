package org.example.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

/**
 * MainControls encapsulates the primary control buttons row used by the main UI.
 * Consumers provide the button handlers via the constructor.
 */
public class MainControls extends HBox {

    public final Button captureButton;
    public final Button recordButton;
    public final Button enrollButton;
    public final Button viewHistoryButton;
    public final Button manageUsersButton;
    public final Button settingsButton;
    public final Button exitButton;

    public MainControls(Runnable onCapture,
                        Runnable onRecord,
                        Runnable onEnroll,
                        Runnable onViewHistory,
                        Runnable onManageUsers,
                        Runnable onSettings,
                        Runnable onExit) {
        super(15);
        setAlignment(Pos.CENTER);

        captureButton = new Button("Capture Photo");
        captureButton.setStyle("-fx-font-size: 14;");
        captureButton.setOnAction(e -> onCapture.run());

        recordButton = new Button("Record Clip (15s)");
        recordButton.setStyle("-fx-font-size: 14;");
        recordButton.setOnAction(e -> onRecord.run());

        enrollButton = new Button("Enroll Unknown");
        enrollButton.setStyle("-fx-font-size: 14; -fx-background-color: #66b3ff; -fx-text-fill: white;");
        enrollButton.setDisable(true);
        enrollButton.setOnAction(e -> onEnroll.run());

        viewHistoryButton = new Button("View History");
        viewHistoryButton.setStyle("-fx-font-size: 14;");
        viewHistoryButton.setOnAction(e -> onViewHistory.run());

        manageUsersButton = new Button("Manage Users");
        manageUsersButton.setStyle("-fx-font-size: 14;");
        manageUsersButton.setOnAction(e -> onManageUsers.run());

        settingsButton = new Button("Settings");
        settingsButton.setStyle("-fx-font-size: 14;");
        settingsButton.setOnAction(e -> onSettings.run());

        exitButton = new Button("Exit");
        exitButton.setStyle("-fx-font-size: 14; -fx-background-color: #ff6666; -fx-text-fill: white;");
        exitButton.setOnAction(e -> onExit.run());

        getChildren().addAll(captureButton, recordButton, enrollButton, viewHistoryButton, manageUsersButton, settingsButton, exitButton);
    }
}


