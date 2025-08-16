package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.model.RecognitionLog;
import org.example.model.User;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * DatabaseService is a wrapper around PostgreSQL operations. It provides
 * methods to initialize the schema and read/write users and recognition logs.
 *
 * Design notes:
 * - Migrated from SQLite to PostgreSQL for robust, multi-user functionality.
 * - Uses username/password authentication.
 * - SQL statements are compliant with PostgreSQL syntax.
 */
public class DatabaseService {

    // --- Database configuration (env-overridable with safe defaults) ---
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5432");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "security_camera_db");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");
    private static final String DATABASE_URL = String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME);

    public DatabaseService() {
        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", DB_USER);
        props.setProperty("password", DB_PASSWORD);
        return DriverManager.getConnection(DATABASE_URL, props);
    }

    private void initializeDatabase() {
        // Use SERIAL PRIMARY KEY for auto-incrementing IDs in PostgreSQL
        String createUserTableSQL = "CREATE TABLE IF NOT EXISTS users (" +
                " id SERIAL PRIMARY KEY," +
                " name TEXT NOT NULL UNIQUE," +
                " role TEXT NOT NULL" +
                ");";

        // Use TIMESTAMP WITH TIME ZONE for better date handling
        String createLogTableSQL = "CREATE TABLE IF NOT EXISTS recognition_log (" +
                " log_id SERIAL PRIMARY KEY," +
                " user_id INTEGER NOT NULL," +
                " timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                " confidence REAL NOT NULL," +
                " FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE" + // Added ON DELETE CASCADE
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUserTableSQL);
            stmt.execute(createLogTableSQL);
            System.out.println("PostgreSQL database and tables are ready.");
        } catch (SQLException e) {
            System.err.println("Error initializing the database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int addUser(String name, String role) {
        // PostgreSQL's SERIAL type handles auto-incrementing. We don't specify the ID.
        // We use "RETURNING id" to get the newly generated ID from the database.
        String insertSQL = "INSERT INTO users(name, role) VALUES(?, ?) RETURNING id";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, name);
            pstmt.setString(2, role);

            // executeQuery is used here because we expect a result set (the returned id)
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int newId = rs.getInt(1);
                System.out.println("Successfully added user '" + name + "' to database with ID/Label: " + newId);
                return newId;
            } else {
                System.err.println("Error adding user '" + name + "'. No ID was returned.");
                return -1;
            }

        } catch (SQLException e) {
            // A common error will be trying to add a user with a name that already exists (violates UNIQUE constraint)
            System.err.println("Error adding user '" + name + "'. They might already exist. " + e.getMessage());
            return -1;
        }
    }

    public Map<Integer, String> getLabelNameMap() {
        Map<Integer, String> labelMap = new HashMap<>();
        String selectSQL = "SELECT id, name, role FROM users";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String role = rs.getString("role");
                labelMap.put(id, role + ": " + name);
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving users from database: " + e.getMessage());
        }
        System.out.println("Loaded " + labelMap.size() + " users from the database.");
        return labelMap;
    }

    public void logRecognition(int userId, double confidence) {
        String sql = "INSERT INTO recognition_log(user_id, confidence) VALUES(?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setDouble(2, confidence);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error logging recognition event: " + e.getMessage());
        }
    }

    public ObservableList<RecognitionLog> getHistory() {
        ObservableList<RecognitionLog> history = FXCollections.observableArrayList();
        String sql = "SELECT u.name, u.role, r.timestamp, r.confidence " +
                "FROM recognition_log r " +
                "JOIN users u ON r.user_id = u.id " +
                "ORDER BY r.timestamp DESC;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                history.add(new RecognitionLog(
                        rs.getString("name"),
                        rs.getString("role"),
                        rs.getTimestamp("timestamp").toString(), // Use getTimestamp
                        rs.getDouble("confidence")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving recognition history: " + e.getMessage());
        }
        return history;
    }

    public ObservableList<User> getUsers() {
        ObservableList<User> users = FXCollections.observableArrayList();
        String sql = "SELECT id, name, role FROM users ORDER BY name;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(new User(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("role")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving users: " + e.getMessage());
        }
        return users;
    }

    public void deleteUser(int userId) {
        // We can rely on the "ON DELETE CASCADE" in the FOREIGN KEY constraint
        // to automatically delete the associated logs. This simplifies the Java code.
        String deleteUserSql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteUserSql)) {

            pstmt.setInt(1, userId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Successfully deleted user " + userId + " and their logs from the database.");
            } else {
                System.err.println("User with ID " + userId + " not found in the database.");
            }

        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
        }
    }
}