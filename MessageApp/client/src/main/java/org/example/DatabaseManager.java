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

/**
 * Manages SQLite database per user for storing encrypted chat states and messages.
 * All sensitive data (keys, message content) is encrypted at rest using AES-GCM.
 */
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Path BASE_DIR = Paths.get("MessageApp", "client", "data");

    private final String url;
    private final SecretKey dbKey;
    private final String username;

    /**
     * Creates or opens a database for the given user.
     *
     * @param username Current logged-in user
     * @param dbKey    Database encryption key (DEK) from keystore
     */
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

    private void initializeDatabase() {
        String createUserSettingsTable = "CREATE TABLE IF NOT EXISTS user_settings (" +
                "key TEXT PRIMARY KEY, " +
                "value TEXT)";

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
                "FOREIGN KEY(recipient_uuid) REFERENCES chat_sessions(recipient_uuid))";

        String createMsgIndex = "CREATE INDEX IF NOT EXISTS idx_messages_recipient_uuid ON messages(recipient_uuid)";

        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            stmt.execute(createUserSettingsTable);
            stmt.execute(createSessionTable);
            stmt.execute(createMsgTable);
            stmt.execute(createMsgIndex);

            log.info("Database schema initialized successfully with latest columns.");

        } catch (SQLException e) {
            log.error("Failed to initialize database", e);
            throw new RuntimeException(e);
        }
    }

    public void saveUserUuid(String uuid) {
        String sql = "INSERT OR REPLACE INTO user_settings (key, value) VALUES ('user_uuid', ?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
            log.info("User UUID saved to database.");
        } catch (SQLException e) {
            log.error("Failed to save user UUID", e);
            throw new RuntimeException(e);
        }
    }

    public String getUserUuid() {
        String sql = "SELECT value FROM user_settings WHERE key = 'user_uuid'";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString("value");
            }
            return null;
        } catch (SQLException e) {
            log.error("Failed to retrieve user UUID", e);
            throw new RuntimeException(e);
        }
    }
    public void upsertChatState(String recipient, String recipientUuid, byte[] sendKey, byte[] recvKey, long sIdx, long rIdx, String sendTag, String recvTag) {
        String sql = "INSERT INTO chat_sessions(recipient_uuid, recipient_name, send_key, receive_key, send_next_idx, receive_next_idx, send_tag, recv_tag) " +
                "VALUES(?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(recipient_uuid) DO UPDATE SET " +
                "recipient_name=excluded.recipient_name, send_key=excluded.send_key, receive_key=excluded.receive_key, " +
                "send_next_idx=excluded.send_next_idx, receive_next_idx=excluded.receive_next_idx, " +
                "send_tag=excluded.send_tag, recv_tag=excluded.recv_tag";

        byte[] aad = CryptoUtils.makeAAD(username, recipient);

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

    public void renameChat(String recipientUuid, String oldName, String newName) {
        log.info("Renaming chat {} from '{}' to '{}'", recipientUuid, oldName, newName);

        byte[] oldAad = CryptoUtils.makeAAD(username, oldName);
        byte[] newAad = CryptoUtils.makeAAD(username, newName);

        String selectChatSql = "SELECT send_key, receive_key FROM chat_sessions WHERE recipient_uuid = ?";
        String updateChatSql = "UPDATE chat_sessions SET recipient_name = ?, send_key = ?, receive_key = ? WHERE recipient_uuid = ?";

        String selectMsgsSql = "SELECT id, content FROM messages WHERE recipient_uuid = ?";
        String updateMsgSql = "UPDATE messages SET content = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(false); // Start transaction

            try {
                // 1. Re-encrypt Chat Keys
                byte[] rawSendKey = null;
                byte[] rawRecvKey = null;

                try (PreparedStatement ps = conn.prepareStatement(selectChatSql)) {
                    ps.setString(1, recipientUuid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            byte[] encSend = rs.getBytes("send_key");
                            byte[] encRecv = rs.getBytes("receive_key");
                            if (encSend != null) rawSendKey = CryptoUtils.decrypt(encSend, dbKey, oldAad);
                            if (encRecv != null) rawRecvKey = CryptoUtils.decrypt(encRecv, dbKey, oldAad);
                        } else {
                            throw new SQLException("Chat not found for UUID: " + recipientUuid);
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(updateChatSql)) {
                    ps.setString(1, newName);
                    ps.setBytes(2, rawSendKey == null ? null : CryptoUtils.encrypt(rawSendKey, dbKey, newAad));
                    ps.setBytes(3, rawRecvKey == null ? null : CryptoUtils.encrypt(rawRecvKey, dbKey, newAad));
                    ps.setString(4, recipientUuid);
                    ps.executeUpdate();
                }

                // 2. Re-encrypt Messages
                try (PreparedStatement psSelect = conn.prepareStatement(selectMsgsSql);
                     PreparedStatement psUpdate = conn.prepareStatement(updateMsgSql)) {

                    psSelect.setString(1, recipientUuid);
                    try (ResultSet rs = psSelect.executeQuery()) {
                        while (rs.next()) {
                            long id = rs.getLong("id");
                            byte[] encContent = rs.getBytes("content");

                            byte[] rawContent = CryptoUtils.decrypt(encContent, dbKey, oldAad);
                            byte[] newEncContent = CryptoUtils.encrypt(rawContent, dbKey, newAad);

                            psUpdate.setBytes(1, newEncContent);
                            psUpdate.setLong(2, id);
                            psUpdate.addBatch();
                        }
                    }
                    psUpdate.executeBatch();
                }

                conn.commit();
                log.info("Chat renamed successfully.");

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }

        } catch (Exception e) {
            log.error("Failed to rename chat", e);
            throw new RuntimeException("Failed to rename chat", e);
        }
    }

    /**
     * Loads a single chat state from database.
     *
     * @param recipient Chat partner username
     * @return PersistedChatState or null if not found
     */
    public PersistedChatState getChatState(String recipient) {
        String sql = "SELECT * FROM chat_sessions WHERE recipient_name = ?";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, recipient);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                byte[] encSend = rs.getBytes("send_key");
                byte[] encRecv = rs.getBytes("receive_key");

                byte[] rawSend = encSend == null ? null : CryptoUtils.decrypt(encSend, dbKey, aad);
                byte[] rawRecv = encRecv == null ? null : CryptoUtils.decrypt(encRecv, dbKey, aad);

                return new PersistedChatState(rs.getString("recipient_name"), rs.getString("recipient_uuid"), rawSend, rawRecv, rs.getLong("send_next_idx"), rs.getLong("receive_next_idx"), rs.getString("send_tag"), rs.getString("recv_tag"));
            }
        } catch (Exception e) {
            log.error("Failed to get chat state for {}", recipient, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads all chat sessions for the user (for login recovery).
     *
     * @return List of all persisted chat states
     */
    public List<PersistedChatState> loadAllChatStates() {
        String sql = "SELECT * FROM chat_sessions";
        List<PersistedChatState> out = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String recipient = rs.getString("recipient_name");
                byte[] encSend = rs.getBytes("send_key");
                byte[] encRecv = rs.getBytes("receive_key");
                byte[] aad = CryptoUtils.makeAAD(username, recipient);

                byte[] rawSend = encSend == null ? null : CryptoUtils.decrypt(encSend, dbKey, aad);
                byte[] rawRecv = encRecv == null ? null : CryptoUtils.decrypt(encRecv, dbKey, aad);

                out.add(new PersistedChatState(recipient, rs.getString("recipient_uuid"), rawSend, rawRecv, rs.getLong("send_next_idx"), rs.getLong("receive_next_idx"), rs.getString("send_tag"), rs.getString("recv_tag")));
            }

            log.info("Loaded {} chat state(s) from database", out.size());
        } catch (Exception e) {
            log.error("Failed to load all chat states", e);
            throw new RuntimeException(e);
        }
        return out;
    }

    /**
     * Saves a message to the database (encrypted).
     *
     * @param recipient   Chat partner username
     * @param messageText Message content
     * @param isSent      True if sent by current user, false if received
     */
    public void addMessage(String recipient, String recipientUuid, String messageText, boolean isSent, boolean isServerSent) {
        String sql = "INSERT INTO messages(recipient_uuid, timestamp, is_sent, is_server_sent, content) VALUES(?,?,?,?,?)";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, recipientUuid);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, isSent ? 1 : 0);
            ps.setInt(4, isServerSent ? 1 : 0);
            ps.setBytes(5, CryptoUtils.encrypt(messageText.getBytes(StandardCharsets.UTF_8), dbKey, aad));
            ps.executeUpdate();

            log.debug("Saved message for recipient: {}", recipient);

        } catch (Exception e) {
            log.error("Failed to add message for {}", recipient, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads all messages for a specific chat.
     *
     * @param recipient Chat partner username
     * @return List of decrypted messages
     */
    public List<Message> loadMessages(String recipient, String recipientUuid) {
        String sql = "SELECT * FROM messages WHERE recipient_uuid = ? ORDER BY timestamp ";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);
        List<Message> messages = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, recipientUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byte[] encContent = rs.getBytes("content");
                    byte[] decContent = CryptoUtils.decrypt(encContent, dbKey, aad);
                    String content = new String(decContent, StandardCharsets.UTF_8);
                    boolean isSent = rs.getInt("is_sent") == 1;

                    // Create Message with appropriate sender
                    String sender = isSent ? username : recipient;
                    messages.add(new Message(sender, content, isSent));
                }
            }

            log.debug("Loaded {} message(s) for recipient: {}", messages.size(), recipient);

        } catch (Exception e) {
            log.error("Failed to load messages for {}", recipient, e);
            throw new RuntimeException(e);
        }

        return messages;
    }

    /**
     * DTO for persisted chat state.
     *
     * @param sendKey raw AES key bytes (or null)
     * @param recvKey raw AES key bytes (or null)
     */
    public record PersistedChatState(String recipient, String recipientUuid, byte[] sendKey, byte[] recvKey, long sendNextIdx,
                                     long recvNextIdx, String sendTag, String recvTag) {
    }

    /**
     * DTO for messages waiting to be sent to the BulletinBoard.
     *
     * @param id          The primary key of the message in the database
     * @param recipient   The intended recipient
     * @param messageText The decrypted message content
     */
    public record PendingMessage(long id, String recipient, String recipientUuid, String messageText) {
    }


    /**
     * Marks a message as successfully sent to the BulletinBoard (sets is_server_sent=1).
     *
     * @param messageId The ID of the message to update.
     */
    public void markMessageAsSent(long messageId) {
        String sql = "UPDATE messages SET is_server_sent = 1 WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, messageId);
            ps.executeUpdate();
            log.debug("Message ID {} marked as delivered to server.", messageId);

        } catch (SQLException e) {
            log.error("Failed to mark message as delivered: {}", messageId, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves all messages that were sent by the user but have not yet
     * been successfully sent to the BulletinBoard (Outbox).
     *
     * @return List of pending messages.
     */
    public List<PendingMessage> getPendingOutboxMessages() {
        // Load messages where is_sent=1 and is_server_sent=0
        // if the issent = 0 it means its a received message
        String sql = "SELECT m.id, c.recipient_name, m.recipient_uuid, m.content " +
                "FROM messages m " +
                "JOIN chat_sessions c ON m.recipient_uuid = c.recipient_uuid " +
                "WHERE m.is_sent = 1 AND m.is_server_sent = 0 " +
                "ORDER BY m.timestamp";
        List<PendingMessage> pending = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String recipient = rs.getString("recipient_name");
                String recipientUuid = rs.getString("recipient_uuid");
                byte[] encContent = rs.getBytes("content");

                byte[] aad = CryptoUtils.makeAAD(username, recipient);
                byte[] decContent = CryptoUtils.decrypt(encContent, dbKey, aad);
                String content = new String(decContent, StandardCharsets.UTF_8);

                pending.add(new PendingMessage(id, recipient, recipientUuid, content));
            }
            log.debug("Found {} pending message(s) in outbox.", pending.size());
        } catch (Exception e) {
            log.error("Failed to load pending outbox messages", e);
            throw new RuntimeException(e);
        }
        return pending;
    }

    /**
     * Transactionally marks a message as sent to the server and updates the sender's chat state.
     */
    public void markMessageAsSentAndUpdateState(long messageId, String recipient, byte[] newSendKey, long newSendIdx, String newSendTag) {
        String markSentSql = "UPDATE messages SET is_server_sent = 1 WHERE id = ?";
        String updateStateSql = "UPDATE chat_sessions SET send_key = ?, send_next_idx = ?, send_tag = ? WHERE recipient_name = ?";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(markSentSql)) {
                ps.setLong(1, messageId);
                ps.executeUpdate();
            }

            // Use the generalized commitMessageState helper to update state and commit
            commitMessageState(newSendKey, newSendIdx, newSendTag, updateStateSql, aad, conn, recipient);
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

    // Replaced commitSentMessageState with a generalized commitMessageState used by both send/receive flows
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

    /**
     * Transactionally adds a received message and updates the receiver's chat state.
     */
    public void addReceivedMessageAndUpdateState(String recipient, String recipientUuid, String messageText, byte[] newRecvKey, long newRecvIdx, String newRecvTag) {
        String addMsgSql = "INSERT INTO messages(recipient_uuid, timestamp, is_sent, is_server_sent, content) VALUES(?,?,?,?,?)";
        String updateStateSql = "UPDATE chat_sessions SET receive_key = ?, receive_next_idx = ?, recv_tag = ? WHERE recipient_uuid = ?";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(addMsgSql)) {
                ps.setString(1, recipientUuid);
                ps.setLong(2, System.currentTimeMillis());
                ps.setInt(3, 0); // is_sent = false
                ps.setInt(4, 1); // is_server_sent = true
                ps.setBytes(5, CryptoUtils.encrypt(messageText.getBytes(StandardCharsets.UTF_8), dbKey, aad));
                ps.executeUpdate();
            }

            // Reuse helper to update receive state and commit transaction
            commitMessageState(newRecvKey, newRecvIdx, newRecvTag, updateStateSql, aad, conn, recipientUuid);

            log.debug("Transactionally added received message for {}", recipient);
        } catch (Exception e) {
            log.error("Transaction failed for received message for {}. Rolling back.", recipient, e);
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

}

