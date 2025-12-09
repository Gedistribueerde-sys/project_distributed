package org.example.controller;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.example.*;
import org.example.GUI.Message;
import org.example.crypto.ChatCrypto;
import org.example.crypto.KeyStoreImpl;
import org.example.proto.ChatProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class ChatCore {
    private static final Logger log = LoggerFactory.getLogger(ChatCore.class);
    private final List<ChatState> activeChats = new ArrayList<>();
    private final KeyStoreImpl keyStore = new KeyStoreImpl();
    private String currentUser;
    private String currentUserUuid;

    private Runnable onMessageUpdateCallback;

    private DatabaseManager databaseManager;

    private final BooleanProperty loggedIn = new SimpleBooleanProperty(false);
    private final BooleanProperty loggedOut = new SimpleBooleanProperty(false);
    private InAndOutBox inAndOutBox;

    public ChatCore() {
    }

    public BooleanProperty loggedInProperty() {
        return loggedIn;
    }

    public BooleanProperty loggedOutProperty() {
        return loggedOut;
    }

    public boolean register(String username, String password) {
        boolean created = keyStore.makeKeyStore(username, password);
        if (created) {
            log.info("Keystore created for user: {}", username);

            // Initialize empty database for new user
            SecretKey dbKey = keyStore.getDatabaseKey();
            if (dbKey != null) {
                databaseManager = new DatabaseManager(username, dbKey);
                // Generate and save user UUID
                String userUuid = java.util.UUID.randomUUID().toString();
                databaseManager.saveUserUuid(userUuid);
                log.info("Database initialized and UUID generated for new user: {}", username);
            }
        } else {
            log.info("Failed to create keystore for user: {}", username);
        }
        return created;
    }

    public boolean login(String username, String password) {
        boolean loaded = keyStore.loadKeyStore(username, password);
        if (loaded) {
            currentUser = username;

            // Get database encryption key from keystore
            SecretKey dbKey = keyStore.getDatabaseKey();
            if (dbKey == null) {
                log.error("Failed to retrieve database key for user {}", username);
                return false;
            }

            // Initialize database manager
            databaseManager = new DatabaseManager(username, dbKey);
            currentUserUuid = databaseManager.getUserUuid();
            if (currentUserUuid == null) {
                log.error("Failed to retrieve user UUID for user {}", username);
                return false;
            }

            // Restore chat states from database
            restoreChatStates();
            // initialise the inand out box
            inAndOutBox = new InAndOutBox(this, databaseManager);
            inAndOutBox.start();

            // Add a shutdown hook to ensure graceful shutdown of the message processor
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered. Stopping message processor...");
                if (inAndOutBox != null) {
                    inAndOutBox.stop();
                    inAndOutBox.join(); // Wait for the thread to finish
                }
                log.info("Message processor stopped gracefully.");
            }));

            log.info("User {} logged in successfully with {} chat(s) restored.", username, activeChats.size());
            loggedIn.set(true);
            loggedOut.set(false);
        } else {
            log.info("Failed login attempt for user: {}", username);
            loggedIn.set(false);
        }
        return loaded;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public String getCurrentUserUuid() {
        return currentUserUuid;
    }

    public InAndOutBox getInAndOutBox() {
        return inAndOutBox;
    }

    public void logout() {

        if (inAndOutBox != null) {
            inAndOutBox.stop();
        }
        log.info("User {} logged out.", currentUser);
        currentUser = null;
        currentUserUuid = null;
        activeChats.clear();
        databaseManager = null;
        loggedIn.set(false);
        loggedOut.set(true);
    }

    /**
     * Restores chat states from database after login.
     */
    private void restoreChatStates() {
        if (databaseManager == null) {
            log.error("Database manager not initialized");
            return;
        }

        try {
            List<DatabaseManager.PersistedChatState> persistedStates = databaseManager.loadAllChatStates();

            // For each persisted chat state, recreate ChatState and load messages
            for (DatabaseManager.PersistedChatState state : persistedStates) {
                SecretKey sendKey = state.sendKey() == null ? null : new SecretKeySpec(state.sendKey(), "AES");
                SecretKey recvKey = state.recvKey() == null ? null : new SecretKeySpec(state.recvKey(), "AES");

                ChatState chat = new ChatState(state.recipient(), state.recipientUuid(), sendKey, state.sendNextIdx(), state.sendTag(), recvKey, state.recvNextIdx(), state.recvTag());

                // Load messages for this chat
                List<Message> messages = databaseManager.loadMessages(state.recipient(), state.recipientUuid());
                for (Message msg : messages) {
                    chat.getMessages().add(msg);
                }

                activeChats.add(chat);
                log.info("Restored chat with {}: {} message(s), sendTag={}, recvTag={}", state.recipient(), messages.size(), state.sendTag(), state.recvTag());
            }
        } catch (Exception e) {
            log.error("Failed to restore chat states", e);
        }
    }

    // Generate a send key encoded as protobuf Base64 string
    // This key can be given to another user so they can receive messages
    public String generateSendKeyInfo() throws Exception {
        ChatProto.KeyInfo keyInfo = ChatCrypto.generateBumpKeyInfo(currentUserUuid);

        byte[] serialized = keyInfo.toByteArray();
        return Base64.getEncoder().encodeToString(serialized);
    }

    // Create a new chat with optional send and receive keys
    public boolean createChatWithKeys(String recipientName, String sendKeyString, String receiveKeyString) {
        try {
            String recipientUuid = null;

            // At least one key must be present
            if ((sendKeyString == null || sendKeyString.isEmpty()) && (receiveKeyString == null || receiveKeyString.isEmpty())) {
                log.error("At least one key (send or receive) must be provided");
                return false;
            }

            SecretKey sendSecretKey = null;
            long sendIdx = 0;
            String sendTag = null;

            SecretKey recvSecretKey = null;
            long recvIdx = 0;
            String recvTag = null;

            // Parse receive key first, as it reliably gives us the recipient's UUID
            if (receiveKeyString != null && !receiveKeyString.trim().isEmpty()) {
                byte[] decoded = Base64.getDecoder().decode(receiveKeyString.trim());
                ChatProto.KeyInfo keyInfo = ChatProto.KeyInfo.parseFrom(decoded);

                recipientUuid = keyInfo.getSenderUuid();

                byte[] keyBytes = keyInfo.getKey().toByteArray();
                recvSecretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
                recvIdx = keyInfo.getIdx();
                recvTag = Base64.getEncoder().encodeToString(keyInfo.getTag().toByteArray());

                log.info("Parsed receive key: idx={}, tag={}", recvIdx, recvTag);
            }

            // Parse send key. Do NOT use it to determine recipientUuid
            if (sendKeyString != null && !sendKeyString.trim().isEmpty()) {
                byte[] decoded = Base64.getDecoder().decode(sendKeyString.trim());
                ChatProto.KeyInfo keyInfo = ChatProto.KeyInfo.parseFrom(decoded);

                // Do NOT set recipientUuid here, it's our UUID, not the recipient's
                // recipientUuid = keyInfo.getSenderUuid(); // REMOVED

                byte[] keyBytes = keyInfo.getKey().toByteArray();
                sendSecretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
                sendIdx = keyInfo.getIdx();
                sendTag = Base64.getEncoder().encodeToString(keyInfo.getTag().toByteArray());

                log.info("Parsed send key: idx={}, tag={}", sendIdx, sendTag);
            }

            if (recipientUuid == null) {
                log.error("Could not extract recipient info from keys. A receive key is required to identify the recipient.");
                return false;
            }

            // Check for duplicate
            String finalRecipientUuid = recipientUuid;
            boolean exists = activeChats.stream().anyMatch(c -> c.recipientUuid.equals(finalRecipientUuid));
            if (exists) {
                log.error("Chat with {} already exists", recipientName);
                return false;
            }


            ChatState chat = new ChatState(recipientName, recipientUuid, sendSecretKey, sendIdx, sendTag, recvSecretKey, recvIdx, recvTag);

            activeChats.add(chat);

            // Persist to database
            if (databaseManager != null) {
                byte[] sendKeyBytes = sendSecretKey == null ? null : sendSecretKey.getEncoded();
                byte[] recvKeyBytes = recvSecretKey == null ? null : recvSecretKey.getEncoded();
                databaseManager.upsertChatState(recipientName, recipientUuid, sendKeyBytes, recvKeyBytes, sendIdx, recvIdx, sendTag, recvTag);
            }

            log.info("Created chat with {}: canSend={}, canReceive={}", recipientName, chat.canSend(), chat.canReceive());
            return true;

        } catch (Exception e) {
            log.error("Error creating chat with keys", e);
            return false;
        }
    }

    public String getDebugStateForIndex(int listIndex) {
        // listIndex = index in the ListView
        // 0 = "➕ New Chat (BUMP)"
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) return "";
        return activeChats.get(idx).debugInfo();
    }

    //  Retrieves the names of the chats for the GUI
    public List<String> getChatNames() {
        List<String> names = new ArrayList<>();
        names.add("+ New Chat (BUMP)");
        activeChats.forEach(chat -> names.add(chat.toString()));
        return names;
    }

    public void renameChat(int listIndex, String newName) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) return;

        ChatState chat = activeChats.get(idx);
        String oldName = chat.recipient;

        if (oldName.equals(newName)) return;

        // Update database first (transactional)
        if (databaseManager != null) {
            databaseManager.renameChat(chat.getRecipientUuid(), newName);
        }

        // Update in-memory state
        chat.setRecipient(newName);

        // Notify UI
        notifyMessageUpdate();
    }

    // sendmessage is now mainly in the inandoutbox
    public void sendMessage(int chatIndex, String message) {
        // 0 = "➕ New Chat (BUMP)", real chats start at 1
        chatIndex -= 1;
        if (chatIndex < 0 || chatIndex >= activeChats.size()) {
            log.error("No valid chat selected for sending message.");
            return;
        }

        ChatState chat = activeChats.get(chatIndex);

        if (!chat.canSend()) {
            log.error("Cannot send - this is a receive-only chat with {}", chat.recipient);
            return;
        }

        try {
            // add it locally to the chat state and database as UNSENT
            // this is for the ui
            chat.addSentMessage(message, currentUser);
            notifyMessageUpdate();


            // save it in the db as unsent
            if (databaseManager != null) {

                long messageId = databaseManager.addMessage(chat.recipient, chat.getRecipientUuid(), message, true, false);
                DatabaseManager.PendingMessage pendingMessage = new DatabaseManager.PendingMessage(messageId, chat.recipient, chat.getRecipientUuid(), message);
                new Thread(() -> inAndOutBox.sendMessageImmediately(pendingMessage)).start();

            }

            log.info("Bericht lokaal gebufferd voor verzending naar {}", chat.recipient);

            // the outboox is trying to send it once it has been added to the db


        } catch (Exception e) {
            log.error("Exception while buffering message", e);
        }
    }

    public ChatState getChatState(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) {
            return null;
        }
        return activeChats.get(idx);
    }

    public List<Message> getMessagesForChat(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) {
            return java.util.Collections.emptyList();
        }
        ChatState chat = activeChats.get(idx);
        return databaseManager.loadMessages(chat.recipient, chat.recipientUuid);
    }

    public boolean canSendToChat(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) return false;
        return activeChats.get(idx).canSend();
    }

    public boolean canReceiveFromChat(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) return false;
        return activeChats.get(idx).canReceive();
    }


    // get the chatstate by recipient name
    public Optional<ChatState> getChatStateByRecipientUuid(String recipientUuid) {
        return activeChats.stream().filter(c -> c.recipientUuid.equals(recipientUuid)).findFirst();
    }


    // gives a snapshot of the active chat states. Used by the outbox
    public List<ChatState> getActiveChatsSnapshot() {
        synchronized (activeChats) {
            return new ArrayList<>(activeChats);
        }
    }


    // Callback registration for message updates
    public void setOnMessageUpdateCallback(Runnable callback) {
        this.onMessageUpdateCallback = callback;
    }

    // Notify GUI of message update
    public void notifyMessageUpdate() {
        if (onMessageUpdateCallback != null) {
            Platform.runLater(onMessageUpdateCallback);
        }
    }
}
