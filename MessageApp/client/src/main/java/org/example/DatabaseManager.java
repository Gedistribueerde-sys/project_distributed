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
        String createSessionTable = "CREATE TABLE IF NOT EXISTS chat_sessions (" + "recipient_id TEXT PRIMARY KEY, " + "send_key BLOB, " + "receive_key BLOB, " + "send_next_idx INTEGER, " + "receive_next_idx INTEGER, " + "send_tag TEXT, " + // New column is now part of the initial creation
                "recv_tag TEXT)";   // New column is now part of the initial creation

        String createMsgTable = "CREATE TABLE IF NOT EXISTS messages (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, " + "recipient_id TEXT, " + "timestamp INTEGER, " + "is_sent INTEGER, " + "content BLOB, " + "is_server_sent INTEGER DEFAULT 0, " + // New column with default is now part of the initial creation
                "FOREIGN KEY(recipient_id) REFERENCES chat_sessions(recipient_id))";

        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            stmt.execute(createSessionTable);
            stmt.execute(createMsgTable);

            log.info("Database schema initialized successfully with latest columns.");

        } catch (SQLException e) {
            log.error("Failed to initialize database", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves or updates chat state. Keys are encrypted before storage.
     *
     * @param recipient Chat partner username
     * @param sendKey   Send key (null if receive-only)
     * @param recvKey   Receive key (null if send-only)
     * @param sIdx      Send index
     * @param rIdx      Receive index
     * @param sendTag   Send tag (Base64 string, can be null)
     * @param recvTag   Receive tag (Base64 string, can be null)
     */
    public void upsertChatState(String recipient, byte[] sendKey, byte[] recvKey, long sIdx, long rIdx, String sendTag, String recvTag) {
        String sql = "INSERT INTO chat_sessions(recipient_id, send_key, receive_key, send_next_idx, receive_next_idx, send_tag, recv_tag) " + "VALUES(?,?,?,?,?,?,?) " + "ON CONFLICT(recipient_id) DO UPDATE SET " + "send_key=excluded.send_key, receive_key=excluded.receive_key, " + "send_next_idx=excluded.send_next_idx, receive_next_idx=excluded.receive_next_idx, " + "send_tag=excluded.send_tag, recv_tag=excluded.recv_tag";

        byte[] aad = CryptoUtils.makeAAD(username, recipient);

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, recipient);
            ps.setBytes(2, sendKey == null ? null : CryptoUtils.encrypt(sendKey, dbKey, aad));
            ps.setBytes(3, recvKey == null ? null : CryptoUtils.encrypt(recvKey, dbKey, aad));
            ps.setLong(4, sIdx);
            ps.setLong(5, rIdx);
            ps.setString(6, sendTag);
            ps.setString(7, recvTag);
            ps.executeUpdate();

            log.debug("Saved chat state for recipient: {}", recipient);

        } catch (Exception e) {
            log.error("Failed to upsert chat state for {}", recipient, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a single chat state from database.
     *
     * @param recipient Chat partner username
     * @return PersistedChatState or null if not found
     */
    public PersistedChatState getChatState(String recipient) {
        String sql = "SELECT * FROM chat_sessions WHERE recipient_id = ?";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, recipient);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                byte[] encSend = rs.getBytes("send_key");
                byte[] encRecv = rs.getBytes("receive_key");

                byte[] rawSend = encSend == null ? null : CryptoUtils.decrypt(encSend, dbKey, aad);
                byte[] rawRecv = encRecv == null ? null : CryptoUtils.decrypt(encRecv, dbKey, aad);

                return new PersistedChatState(recipient, rawSend, rawRecv, rs.getLong("send_next_idx"), rs.getLong("receive_next_idx"), rs.getString("send_tag"), rs.getString("recv_tag"));
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
                String recipient = rs.getString("recipient_id");
                byte[] encSend = rs.getBytes("send_key");
                byte[] encRecv = rs.getBytes("receive_key");
                byte[] aad = CryptoUtils.makeAAD(username, recipient);

                byte[] rawSend = encSend == null ? null : CryptoUtils.decrypt(encSend, dbKey, aad);
                byte[] rawRecv = encRecv == null ? null : CryptoUtils.decrypt(encRecv, dbKey, aad);

                out.add(new PersistedChatState(recipient, rawSend, rawRecv, rs.getLong("send_next_idx"), rs.getLong("receive_next_idx"), rs.getString("send_tag"), rs.getString("recv_tag")));
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
    public void addMessage(String recipient, String messageText, boolean isSent, boolean isServerSent) {
        String sql = "INSERT INTO messages(recipient_id, timestamp, is_sent, is_server_sent, content) VALUES(?,?,?,?,?)";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, recipient);
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
    public List<Message> loadMessages(String recipient) {
        String sql = "SELECT * FROM messages WHERE recipient_id = ? ORDER BY timestamp ";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);
        List<Message> messages = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, recipient);
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
    public record PersistedChatState(String recipient, byte[] sendKey, byte[] recvKey, long sendNextIdx,
                                     long recvNextIdx, String sendTag, String recvTag) {
    }

    /**
     * DTO for messages waiting to be sent to the BulletinBoard.
     *
     * @param id          The primary key of the message in the database
     * @param recipient   The intended recipient
     * @param messageText The decrypted message content
     */
    public record PendingMessage(long id, String recipient, String messageText) {
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
        String sql = "SELECT id, recipient_id, content FROM messages WHERE is_sent = 1 AND is_server_sent = 0 ORDER BY timestamp ";
        List<PendingMessage> pending = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String recipient = rs.getString("recipient_id");
                byte[] encContent = rs.getBytes("content");

                byte[] aad = CryptoUtils.makeAAD(username, recipient);
                byte[] decContent = CryptoUtils.decrypt(encContent, dbKey, aad);
                String content = new String(decContent, StandardCharsets.UTF_8);

                pending.add(new PendingMessage(id, recipient, content));
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
        String updateStateSql = "UPDATE chat_sessions SET send_key = ?, send_next_idx = ?, send_tag = ? WHERE recipient_id = ?";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(markSentSql)) {
                ps.setLong(1, messageId);
                ps.executeUpdate();
            }

            commitSentMessageState(recipient, newSendKey, newSendIdx, newSendTag, updateStateSql, aad, conn);
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

    private void commitSentMessageState(String recipient, byte[] newSendKey, long newSendIdx, String newSendTag, String updateStateSql, byte[] aad, Connection conn) throws SQLException, GeneralSecurityException {
        try (PreparedStatement ps = conn.prepareStatement(updateStateSql)) {
            ps.setBytes(1, newSendKey == null ? null : CryptoUtils.encrypt(newSendKey, dbKey, aad));
            ps.setLong(2, newSendIdx);
            ps.setString(3, newSendTag);
            ps.setString(4, recipient);
            ps.executeUpdate();
        }

        conn.commit();
    }

    /**
     * Transactionally adds a received message and updates the receiver's chat state.
     */
    public void addReceivedMessageAndUpdateState(String recipient, String messageText, byte[] newRecvKey, long newRecvIdx, String newRecvTag) {
        String addMsgSql = "INSERT INTO messages(recipient_id, timestamp, is_sent, is_server_sent, content) VALUES(?,?,?,?,?)";
        String updateStateSql = "UPDATE chat_sessions SET receive_key = ?, receive_next_idx = ?, recv_tag = ? WHERE recipient_id = ?";
        byte[] aad = CryptoUtils.makeAAD(username, recipient);
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(addMsgSql)) {
                ps.setString(1, recipient);
                ps.setLong(2, System.currentTimeMillis());
                ps.setInt(3, 0); // is_sent = false
                ps.setInt(4, 1); // is_server_sent = true
                ps.setBytes(5, CryptoUtils.encrypt(messageText.getBytes(StandardCharsets.UTF_8), dbKey, aad));
                ps.executeUpdate();
            }

            commitSentMessageState(recipient, newRecvKey, newRecvIdx, newRecvTag, updateStateSql, aad, conn);
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

