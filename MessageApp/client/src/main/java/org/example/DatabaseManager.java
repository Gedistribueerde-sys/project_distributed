package org.example;

import org.example.GUI.Message;
import org.example.crypto.CryptoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Manages the SQLite database for storing user settings, chat states, and messages
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Path BASE_DIR = Paths.get("MessageApp", "client", "data");

    private final String url;
    private final SecretKey dbKey;
    private final String username;
    
    public DatabaseManager(String username, SecretKey dbKey) {
        this.username = username;
        this.dbKey = dbKey;

        try {
            Files.createDirectories(BASE_DIR);
        } catch (IOException e) {
            log.error("Failed to create database directory", e);
        }

        this.url = "jdbc:sqlite:" + BASE_DIR.resolve("user_" + username + ".db");
        initializeDatabase();
        log.info("Database initialized for user: {}", username);
    }

    // Initializes the database schema if not already present
    private void initializeDatabase() {
        String createUserSettingsTable = "CREATE TABLE IF NOT EXISTS user_settings (key TEXT PRIMARY KEY, value TEXT)";

        String createSessionTable = "CREATE TABLE IF NOT EXISTS chat_sessions (" +
                "recipient_uuid TEXT PRIMARY KEY, " +
                "recipient_name TEXT, " +
                "send_key BLOB, " +
                "receive_key BLOB, " +
                "send_next_idx INTEGER, " +
                "receive_next_idx INTEGER, " +
                "send_tag TEXT, " +
                "recv_tag TEXT)";

        String createMsgTable = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "recipient_uuid TEXT, " +
                "timestamp INTEGER, " +
                "is_sent INTEGER, " +
                "content BLOB, " +
                "is_server_sent INTEGER DEFAULT 0, " +
                "proposed_next_idx INTEGER, " +
                "proposed_next_tag TEXT, " +
                "proposed_next_key BLOB, " +
                "FOREIGN KEY(recipient_uuid) REFERENCES chat_sessions(recipient_uuid))";

        String createPendingConfirmationsTable = "CREATE TABLE IF NOT EXISTS pending_confirmations (" +
                "message_id INTEGER PRIMARY KEY, " +
                "recv_idx INTEGER NOT NULL, " +
                "recv_tag TEXT NOT NULL, " +
                "FOREIGN KEY(message_id) REFERENCES messages(id))";

        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            stmt.execute(createUserSettingsTable);
            stmt.execute(createSessionTable);
            stmt.execute(createMsgTable);
            stmt.execute(createPendingConfirmationsTable);
            log.info("Database schema initialized successfully.");
        } catch (SQLException e) {
            log.error("Failed to initialize database", e);
            throw new RuntimeException(e);
        }
    }

    // Saves the user's UUID to the database
    public void saveUserUuid(String uuid) {
        String sql = "INSERT OR REPLACE INTO user_settings (key, value) VALUES ('user_uuid', ?)";
        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
            log.info("User UUID saved to database.");
        } catch (SQLException e) {
            log.error("Failed to save user UUID", e);
            throw new RuntimeException(e);
        }
    }

    // Retrieves the user's UUID from the database

    // try-with-resources wordt gebruikt zodat Connection, PreparedStatement en ResultSet
    // automatisch worden gesloten, ook bij fouten (voorkomt memory leaks).
    // Connection: maakt de verbinding met de database.
    // PreparedStatement: voert de SQL-query veilig en efficiënt uit.
    // ResultSet: bevat het resultaat van de SELECT-query.
    // Samen zorgen ze voor correcte, veilige en onderhoudbare database-toegang.
    public String getUserUuid() {
        String sql = "SELECT value FROM user_settings WHERE key = 'user_uuid'";
        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString("value");
            }
            return null;
        } catch (SQLException e) {
            log.error("Failed to retrieve user UUID", e);
            throw new RuntimeException(e);
        }
    }

    // Inserts or updates the chat state for a given recipient
    public void upsertChatState(String recipient, String recipientUuid, byte[] sendKey, byte[] recvKey, long sIdx, long rIdx, String sendTag, String recvTag) {
        String sql = "INSERT INTO chat_sessions(recipient_uuid, recipient_name, send_key, receive_key, send_next_idx, receive_next_idx, send_tag, recv_tag) " +
                "VALUES(?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(recipient_uuid) DO UPDATE SET " +
                "recipient_name=excluded.recipient_name, send_key=excluded.send_key, receive_key=excluded.receive_key, " +
                "send_next_idx=excluded.send_next_idx, receive_next_idx=excluded.receive_next_idx, " +
                "send_tag=excluded.send_tag, recv_tag=excluded.recv_tag";
        // AAD (Additional Authenticated Data) wordt aangemaakt voor extra beveiliging.
        // Het zorgt ervoor dat de versleutelde data alleen geldig is voor deze specifieke
        // combinatie van gebruiker (username) en ontvanger (recipientUuid).
        // Zo kan een bericht niet geldig hergebruikt worden in een andere context.
        byte[] aad = CryptoUtils.makeAAD(username, recipientUuid);

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recipientUuid);
            ps.setString(2, recipient);
            ps.setBytes(3, sendKey == null ? null : CryptoUtils.encrypt(sendKey, dbKey, aad));
            ps.setBytes(4, recvKey == null ? null : CryptoUtils.encrypt(recvKey, dbKey, aad));
            ps.setLong(5, sIdx);
            ps.setLong(6, rIdx);
            ps.setString(7, sendTag);
            ps.setString(8, recvTag);
            ps.executeUpdate();
            log.debug("Saved chat state for recipient: {}", recipient);
        } catch (Exception e) {
            log.error("Failed to upsert chat state for {}", recipient, e);
            throw new RuntimeException(e);
        }
    }

    // Renames a chat session
    public void renameChat(String recipientUuid, String newName) {
        log.info("Renaming chat {} to '{}'", recipientUuid, newName);
        String updateChatSql = "UPDATE chat_sessions SET recipient_name = ? WHERE recipient_uuid = ?";

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(updateChatSql)) {
            ps.setString(1, newName);
            ps.setString(2, recipientUuid);
            ps.executeUpdate();
            log.info("Chat renamed successfully.");
        } catch (Exception e) {
            log.error("Failed to rename chat", e);
            throw new RuntimeException("Failed to rename chat", e);
        }
    }

    // Loads all chat states from the database
    public List<PersistedChatState> loadAllChatStates() {
        String sql = "SELECT * FROM chat_sessions";
        List<PersistedChatState> out = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String recipient = rs.getString("recipient_name");
                String recipientUuid = rs.getString("recipient_uuid");
                byte[] encSend = rs.getBytes("send_key");
                byte[] encRecv = rs.getBytes("receive_key");
                byte[] aad = CryptoUtils.makeAAD(username, recipientUuid);
                byte[] rawSend = encSend == null ? null : CryptoUtils.decrypt(encSend, dbKey, aad);
                byte[] rawRecv = encRecv == null ? null : CryptoUtils.decrypt(encRecv, dbKey, aad);
                out.add(new PersistedChatState(recipient, recipientUuid, rawSend, rawRecv, rs.getLong("send_next_idx"), rs.getLong("receive_next_idx"), rs.getString("send_tag"), rs.getString("recv_tag")));
            }
            log.info("Loaded {} chat state(s) from database", out.size());
        } catch (Exception e) {
            log.error("Failed to load all chat states", e);
            throw new RuntimeException(e);
        }
        return out;
    }

    // Adds a message to the database
    public long addMessage(String recipient, String recipientUuid, String messageText, boolean isSent, boolean isServerSent) {
        String sql = "INSERT INTO messages(recipient_uuid, timestamp, is_sent, is_server_sent, content) VALUES(?,?,?,?,?)";
        byte[] aad = CryptoUtils.makeAAD(username, recipientUuid);
        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, recipientUuid);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, isSent ? 1 : 0);
            ps.setInt(4, isServerSent ? 1 : 0);
            ps.setBytes(5, CryptoUtils.encrypt(messageText.getBytes(StandardCharsets.UTF_8), dbKey, aad));
            ps.executeUpdate();
            log.debug("Saved message for recipient: {}", recipient);

            // Deze ResultSet bevat de automatisch gegenereerde sleutels (meestal de primary key)
            // van de zojuist ingevoegde database-rij.
            // ps.getGeneratedKeys() haalt dat ID op dat door de database is aangemaakt.
            // Met rs.next() ga je naar de eerste (en enige) sleutel.
            // Zo kan het bericht later opnieuw worden teruggevonden of geüpdatet.
            // precompilen van de statement
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                } else {
                    throw new SQLException("Creating message failed, no ID obtained.");
                }
            }
        } catch (Exception e) {
            log.error("Failed to add message for {}", recipient, e);
            throw new RuntimeException(e);
        }
    }

    // Loads all messages for a given recipient
    public List<Message> loadMessages(String recipient, String recipientUuid) {
        String sql = "SELECT * FROM messages WHERE recipient_uuid = ? ORDER BY timestamp ";
        byte[] aad = CryptoUtils.makeAAD(username, recipientUuid);
        List<Message> messages = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recipientUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byte[] encContent = rs.getBytes("content");
                    byte[] decContent = CryptoUtils.decrypt(encContent, dbKey, aad);
                    String content = new String(decContent, StandardCharsets.UTF_8);
                    boolean isSent = rs.getInt("is_sent") == 1;
                    boolean isServerSent = rs.getInt("is_server_sent") == 1;
                    String sender = isSent ? username : recipient;

                    // Determine message status
                    Message.MessageStatus status;
                    if (isSent) {
                        status = isServerSent ? Message.MessageStatus.SENT : Message.MessageStatus.PENDING;
                    } else {
                        status = Message.MessageStatus.DELIVERED;
                    }

                    // Get timestamp from database
                    long timestampMillis = rs.getLong("timestamp");
                    java.time.LocalDateTime timestamp = java.time.Instant.ofEpochMilli(timestampMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();

                    messages.add(new Message(sender, content, isSent, status, timestamp));
                }
            }
            log.debug("Loaded {} message(s) for recipient: {}", messages.size(), recipient);
        } catch (Exception e) {
            log.error("Failed to load messages for {}", recipient, e);
            throw new RuntimeException(e);
        }
        return messages;
    }

    // Represents the persisted chat state loaded from the database
    public record PersistedChatState(String recipient, String recipientUuid, byte[] sendKey, byte[] recvKey, long sendNextIdx, long recvNextIdx, String sendTag, String recvTag) {}

    // Represents a pending outbox message with proposed next state values
    public record PendingMessage(long id, String recipient, String recipientUuid, String messageText,
                                    Long proposedNextIdx, String proposedNextTag, byte[] proposedNextKey) {}

    // Loads all pending outbox messages that have been sent but not yet confirmed by the server
    public List<PendingMessage> getPendingOutboxMessages() {
        String sql = "SELECT m.id, c.recipient_name, m.recipient_uuid, m.content, " +
                "m.proposed_next_idx, m.proposed_next_tag, m.proposed_next_key FROM messages m " +
                "JOIN chat_sessions c ON m.recipient_uuid = c.recipient_uuid " +
                "WHERE m.is_sent = 1 AND m.is_server_sent = 0 ORDER BY m.timestamp";
        List<PendingMessage> pending = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String recipient = rs.getString("recipient_name");
                String recipientUuid = rs.getString("recipient_uuid");
                byte[] encContent = rs.getBytes("content");
                byte[] aad = CryptoUtils.makeAAD(username, recipientUuid);
                byte[] decContent = CryptoUtils.decrypt(encContent, dbKey, aad);
                String content = new String(decContent, StandardCharsets.UTF_8);

                // Retrieve proposed values (may be null if not yet set)
                Long proposedNextIdx = rs.getObject("proposed_next_idx") != null ? rs.getLong("proposed_next_idx") : null;
                String proposedNextTag = rs.getString("proposed_next_tag");
                byte[] encProposedKey = rs.getBytes("proposed_next_key");
                byte[] proposedNextKey = encProposedKey != null ? CryptoUtils.decrypt(encProposedKey, dbKey, aad) : null;

                pending.add(new PendingMessage(id, recipient, recipientUuid, content, proposedNextIdx, proposedNextTag, proposedNextKey));
            }
        } catch (Exception e) {
            log.error("Failed to load pending outbox messages", e);
            throw new RuntimeException(e);
        }
        return pending;
    }

    // Saves the proposed next state values for a sent message
    public void saveProposedSendValues(long messageId, String recipientUuid, long proposedNextIdx, String proposedNextTag, byte[] proposedNextKey) {
        String sql = "UPDATE messages SET proposed_next_idx = ?, proposed_next_tag = ?, proposed_next_key = ? WHERE id = ?";
        byte[] aad = CryptoUtils.makeAAD(username, recipientUuid);
        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposedNextIdx);
            ps.setString(2, proposedNextTag);
            ps.setBytes(3, CryptoUtils.encrypt(proposedNextKey, dbKey, aad));
            ps.setLong(4, messageId);
            ps.executeUpdate();
            log.debug("Saved proposed send values for message {}: idx={}, tag={}", messageId, proposedNextIdx, proposedNextTag);
        } catch (Exception e) {
            log.error("Failed to save proposed send values for message {}", messageId, e);
            throw new RuntimeException(e);
        }
    }

    // Marks a message as sent by the server and updates the chat state transactionally
    public void markMessageAsSentAndUpdateState(long messageId, String recipient, byte[] newSendKey, long newSendIdx, String newSendTag) {
        String markSentSql = "UPDATE messages SET is_server_sent = 1, proposed_next_idx = NULL, proposed_next_tag = NULL, proposed_next_key = NULL WHERE id = ?";
        String updateStateSql = "UPDATE chat_sessions SET send_key = ?, send_next_idx = ?, send_tag = ? WHERE recipient_uuid = ?";
        String getUuidSql = "SELECT recipient_uuid FROM chat_sessions WHERE recipient_name = ?";

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false); // 2 dingen tegelijk doen

            String recipientUuid;
            try (PreparedStatement ps = conn.prepareStatement(getUuidSql)) {
                ps.setString(1, recipient);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        recipientUuid = rs.getString("recipient_uuid");
                    } else {
                        throw new SQLException("Recipient not found: " + recipient);
                    }
                }
            }

            byte[] aad = CryptoUtils.makeAAD(username, recipientUuid);

            try (PreparedStatement ps = conn.prepareStatement(markSentSql)) {
                ps.setLong(1, messageId);
                ps.executeUpdate();
            }
            commitMessageState(newSendKey, newSendIdx, newSendTag, updateStateSql, aad, conn, recipientUuid);
            log.debug("Transactionally updated state for sent message {}", messageId);
        } catch (Exception e) {
            log.error("Transaction failed for sent message {}. Rolling back.", messageId, e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("Failed to rollback transaction", ex);
                }
            }
            throw new RuntimeException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Failed to close connection", e);
                }
            }
        }
    }

    // Commits the new message state within a transaction
    private void commitMessageState(byte[] newKey, long newIdx, String newTag, String updateStateSql, byte[] aad, Connection conn, String recipientParam) throws SQLException, GeneralSecurityException {
        try (PreparedStatement ps = conn.prepareStatement(updateStateSql)) {
            ps.setBytes(1, newKey == null ? null : CryptoUtils.encrypt(newKey, dbKey, aad));
            ps.setLong(2, newIdx);
            ps.setString(3, newTag);
            ps.setString(4, recipientParam);
            ps.executeUpdate();
        }
        conn.commit();
    }

    // Adds a received message and updates the chat state transactionally
    public void addReceivedMessageAndUpdateState(String recipient, String recipientUuid, String messageText, long currentRecvIdx, String currentRecvTag, byte[] newRecvKey, long newRecvIdx, String newRecvTag) {
        String addMsgSql = "INSERT INTO messages(recipient_uuid, timestamp, is_sent, content) VALUES(?,?,?,?)";
        String addConfirmSql = "INSERT INTO pending_confirmations(message_id, recv_idx, recv_tag) VALUES(?,?,?)";
        String updateStateSql = "UPDATE chat_sessions SET receive_key = ?, receive_next_idx = ?, recv_tag = ? WHERE recipient_uuid = ?";
        byte[] aad = CryptoUtils.makeAAD(username, recipientUuid);
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);
            long messageId;
            try (PreparedStatement ps = conn.prepareStatement(addMsgSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, recipientUuid);
                ps.setLong(2, System.currentTimeMillis());
                ps.setInt(3, 0); // is_sent = false
                ps.setBytes(4, CryptoUtils.encrypt(messageText.getBytes(StandardCharsets.UTF_8), dbKey, aad));
                ps.executeUpdate();
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        messageId = generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("Creating message failed, no ID obtained.");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(addConfirmSql)) {
                ps.setLong(1, messageId);
                ps.setLong(2, currentRecvIdx);
                ps.setString(3, currentRecvTag);
                ps.executeUpdate();
            }
            commitMessageState(newRecvKey, newRecvIdx, newRecvTag, updateStateSql, aad, conn, recipientUuid);
            log.debug("Transactionally added received message for {}", recipient);
        } catch (Exception e) {
            log.error("Transaction failed for received message for {}. Rolling back.", recipient, e);
            if (conn != null) {
                try {
                    conn.rollback(); //wordt gebruikt om alle databasewijzigingen in deze transactie ongedaan te maken als er ergens een fout optreedt, zodat je database consistent blijft.
                } catch (SQLException ex) {
                    log.error("Failed to rollback transaction", ex);
                }
            }
            throw new RuntimeException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Failed to close connection", e);
                }
            }
        }
    }

    // Represents an unconfirmed received message
    public record UnconfirmedMessage(long messageId, long recvIdx, String recvTag) {}

    // Retrieves all unconfirmed received messages
    public List<UnconfirmedMessage> getUnconfirmedMessages() {
        String sql = "SELECT message_id, recv_idx, recv_tag FROM pending_confirmations";
        List<UnconfirmedMessage> messages = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                messages.add(new UnconfirmedMessage(rs.getLong("message_id"), rs.getLong("recv_idx"), rs.getString("recv_tag")));
            }
        } catch (SQLException e) {
            log.error("Failed to retrieve unconfirmed messages", e);
            throw new RuntimeException(e);
        }
        return messages;
    }

    // Deletes a pending confirmation entry after the message has been confirmed
    public void deletePendingConfirmation(long messageId) {
        String sql = "DELETE FROM pending_confirmations WHERE message_id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete pending confirmation for message: {}", messageId, e);
            throw new RuntimeException(e);
        }
    }
}

