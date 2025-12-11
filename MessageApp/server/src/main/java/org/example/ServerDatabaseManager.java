package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class ServerDatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(ServerDatabaseManager.class);
    private final String dbUrl;

    public ServerDatabaseManager(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
    }

    public void initializeDatabase() {
        String sql = """
                CREATE TABLE IF NOT EXISTS bulletin_board (
                    cell_index INTEGER NOT NULL,
                    board_capacity INTEGER NOT NULL,
                    message_tag TEXT PRIMARY KEY,
                    message_value BLOB NOT NULL
                );
                """;

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("Database initialized successfully. Table 'bulletin_board' is ready.");
        } catch (SQLException e) {
            log.error("Error initializing the database", e);
            throw new RuntimeException("Failed to initialize the database", e);
        }
    }

    public void saveMessage(int cellIndex, int boardCapacity, String messageTag, byte[] messageValue) {
        String sql = "INSERT INTO bulletin_board(cell_index, board_capacity, message_tag, message_value) VALUES(?,?,?,?)";

        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cellIndex);
            stmt.setInt(2, boardCapacity);
            stmt.setString(3, messageTag);
            stmt.setBytes(4, messageValue);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error saving message with tag '{}'", messageTag, e);
            // Re-throw as a runtime exception to notify the caller of the failure
            throw new RuntimeException("Failed to save message", e);
        }
    }

    public void deleteMessage(String messageTag) {
        String sql = "DELETE FROM bulletin_board WHERE message_tag = ?";

        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, messageTag);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error deleting message with tag '{}'", messageTag, e);
            // Re-throw as a runtime exception to notify the caller of the failure
            throw new RuntimeException("Failed to delete message", e);
        }
    }

    private Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        // Set PRAGMA for durability to prevent data loss on crash
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA synchronous = FULL;");
        }
        return conn;
    }
    public record PersistedMessage(int cellIndex, int boardCapacity, String messageTag, byte[] messageValue) {}
    public List<Integer> getAllBoardCapacities() {
        String sql = "SELECT DISTINCT board_capacity FROM bulletin_board";
        List<Integer> capacities = new ArrayList<>();

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                capacities.add(rs.getInt("board_capacity"));
            }
        } catch (SQLException e) {
            log.error("Error retrieving board capacities", e);
            throw new RuntimeException("Failed to retrieve capacities", e);
        }
        return capacities;
    }
    public List<PersistedMessage> loadAllMessagesWithCapacity() {
        String sql = "SELECT cell_index, board_capacity, message_tag, message_value FROM bulletin_board";
        List<PersistedMessage> messages = new ArrayList<>();

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                messages.add(new PersistedMessage(
                        rs.getInt("cell_index"),
                        rs.getInt("board_capacity"),
                        rs.getString("message_tag"),
                        rs.getBytes("message_value")
                ));
            }
            log.info("Loaded {} raw messages from the database.", messages.size());
        } catch (SQLException e) {
            log.error("Error loading messages from the database", e);
            throw new RuntimeException("Failed to load messages from the database", e);
        }
        return messages;
    }
}


