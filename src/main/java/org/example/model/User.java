package org.example.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * User is a minimal JavaFX-friendly model for displaying and managing
 * known people in the system. It is backed by the SQLite `users` table.
 */
public class User {
    private final SimpleIntegerProperty id;
    private final SimpleStringProperty name;
    private final SimpleStringProperty role;

    public User(int id, String name, String role) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.role = new SimpleStringProperty(role);
    }

    // Getters are required for the PropertyValueFactory to work
    public int getId() { return id.get(); }
    public String getName() { return name.get(); }
    public String getRole() { return role.get(); }
}


