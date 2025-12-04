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

    public List<Map<String, byte[]>> loadAllMessages(int boardSize) {
        String sql = "SELECT cell_index, message_tag, message_value FROM bulletin_board";
        List<Map<String, byte[]>> boardState = new ArrayList<>(boardSize);
        for (int i = 0; i < boardSize; i++) {
            boardState.add(new ConcurrentHashMap<>());
        }

        int messageCount = 0;
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int cellIndex = rs.getInt("cell_index");
                String messageTag = rs.getString("message_tag");
                byte[] messageValue = rs.getBytes("message_value");

                if (cellIndex >= 0 && cellIndex < boardSize) {
                    boardState.get(cellIndex).put(messageTag, messageValue);
                    messageCount++;
                } else {
                    log.warn("Found message with out-of-bounds cell_index {} in database. Ignoring.", cellIndex);
                }
            }

            log.info("Loaded {} messages from the database into {} cells.", messageCount, boardSize);
        } catch (SQLException e) {
            log.error("Error loading messages from the database", e);
            throw new RuntimeException("Failed to load messages from the database", e);
        }

        return boardState;
    }

    public void saveMessage(int cellIndex, String messageTag, byte[] messageValue) {
        String sql = "INSERT INTO bulletin_board(cell_index, message_tag, message_value) VALUES(?,?,?)";

        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cellIndex);
            stmt.setString(2, messageTag);
            stmt.setBytes(3, messageValue);
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

}


