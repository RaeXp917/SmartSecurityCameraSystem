package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.DatabaseService;
import org.example.FaceRecognitionService;
import org.example.model.User;

import java.util.Optional;

/**
 * UserManagementWindow provides a simple UI to view and delete users.
 * Deleting a user will also delete their logs and training data and
 * trigger a background retrain.
 */
public class UserManagementWindow {

    private final DatabaseService databaseService;
    private final FaceRecognitionService recognitionService;
    private final Runnable onAfterRetrain; // Callback to update status from the main app

    public UserManagementWindow(DatabaseService databaseService,
                                FaceRecognitionService recognitionService,
                                Runnable onAfterRetrain) {
        this.databaseService = databaseService;
        this.recognitionService = recognitionService;
        this.onAfterRetrain = onAfterRetrain;
    }

    public void show(Stage owner) {
        Stage userStage = new Stage();
        userStage.setTitle("User Management");
        userStage.initOwner(owner);
        userStage.initModality(Modality.APPLICATION_MODAL);

        TableView<User> userTable = new TableView<>(databaseService.getUsers());
        TableColumn<User, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<User, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<User, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
        userTable.getColumns().addAll(idCol, nameCol, roleCol);
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button deleteButton = new Button("Delete Selected User");
        deleteButton.setDisable(true);
        deleteButton.setStyle("-fx-background-color: #ff8080; -fx-text-fill: white;");

        userTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            deleteButton.setDisable(newSelection == null);
        });

        deleteButton.setOnAction(e -> {
            User selectedUser = userTable.getSelectionModel().getSelectedItem();
            if (selectedUser != null) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirm Deletion");
                alert.setHeaderText("Delete User: " + selectedUser.getName());
                alert.setContentText("Are you sure you want to permanently delete this user, all their recognition logs, and all their training photos? This action cannot be undone.");

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    databaseService.deleteUser(selectedUser.getId());
                    recognitionService.deleteTrainingData(selectedUser.getRole(), selectedUser.getName());
                    userTable.setItems(databaseService.getUsers());
                    new Thread(onAfterRetrain).start();
                }
            }
        });

        VBox layout = new VBox(10, userTable, deleteButton);
        layout.setPadding(new Insets(10));
        layout.setAlignment(Pos.CENTER);

        Scene scene = new Scene(layout, 500, 400);
        userStage.setScene(scene);
        userStage.showAndWait();
    }
}


