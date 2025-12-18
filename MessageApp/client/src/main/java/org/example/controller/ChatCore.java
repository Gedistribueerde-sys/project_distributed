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

/**
 * Optional<T> is een containerobject dat wel of geen waarde van type T kan bevatten.
 *
 * Het wordt gebruikt om null te vermijden en expliciet te maken dat een waarde kan ontbreken.
 *
 * Met methodes zoals isPresent(), orElse() en ifPresent() kan je veilig met die waarde werken zonder NullPointerException.
 Hier wordt Optional<ChatState> gebruikt om expliciet en veilig aan te geven dat een chat mogelijk niet bestaat, zonder null te moeten teruggeven
 */
public class ChatCore {
    private static final Logger log = LoggerFactory.getLogger(ChatCore.class);
    private final List<ChatState> userChats = new ArrayList<>();
    private final KeyStoreImpl keyStore = new KeyStoreImpl();
    private String currentUser;
    private String currentUserUuid;

    // Track the currently displayed/active chat for fast polling
    private volatile String activeChatUuid = null;

    // Callback for message updates
    private Runnable onMessageUpdateCallback;

    // Database manager for persisting chat states and messages
    private DatabaseManager databaseManager;
    // Waarom een BooleanProperty i.p.v. een gewone boolean?
    // - Omdat JavaFX Properties "observable" zijn: de UI kan automatisch mee updaten
    //   wanneer de waarde verandert (binding / listeners).
    private final BooleanProperty loggedIn = new SimpleBooleanProperty(false);
    private final BooleanProperty loggedOut = new SimpleBooleanProperty(false);

    // InAndOutBox for message processing
    private InAndOutBox inAndOutBox;

    public BooleanProperty loggedInProperty() {
        return loggedIn;
    }

    public BooleanProperty loggedOutProperty() {
        return loggedOut;
    }

    // User registration
    public boolean register(String username, String password) {
        boolean created = keyStore.makeKeyStore(username, password); // creates new keystore
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

    // User login
    public boolean login(String username, String password) {
        boolean loaded = keyStore.loadKeyStore(username, password); // loads existing keystore
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
            // Initialize and start InAndOutBox for message processing
            inAndOutBox = new InAndOutBox(this, databaseManager);
            inAndOutBox.start(); // moet denkik niet meer gebruikt worden

            // Add a shutdown hook to ensure graceful shutdown of the message processor
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered. Stopping message processor...");
                if (inAndOutBox != null) {
                    inAndOutBox.stop();
                    inAndOutBox.join(); // Wait for the thread to finish
                }
                log.info("Message processor stopped gracefully.");
            }));

            log.info("User {} logged in successfully with {} chat(s) restored.", username, userChats.size());
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
        userChats.clear();
        databaseManager = null;
        loggedIn.set(false);
        loggedOut.set(true);
    }

    // Restore chat states from the database
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

                userChats.add(chat);
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

        byte[] serialized = keyInfo.toByteArray(); // Serialize the KeyInfo message
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

            // Parse receive key first if present, determine recipientUuid from it
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

            // Parse send key if present
            if (sendKeyString != null && !sendKeyString.trim().isEmpty()) {
                byte[] decoded = Base64.getDecoder().decode(sendKeyString.trim());
                ChatProto.KeyInfo keyInfo = ChatProto.KeyInfo.parseFrom(decoded);

                byte[] keyBytes = keyInfo.getKey().toByteArray();
                sendSecretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
                sendIdx = keyInfo.getIdx();
                sendTag = Base64.getEncoder().encodeToString(keyInfo.getTag().toByteArray());

                log.info("Parsed send key: idx={}, tag={}", sendIdx, sendTag);
            }

            if (recipientUuid == null) {
                // If no receive key was provided, but a send key was, generate a random UUID as a placeholder
                if (sendKeyString != null && !sendKeyString.trim().isEmpty()) {
                    recipientUuid = java.util.UUID.randomUUID().toString(); //universally unique identifier (UUID).
                    log.warn("No receive key provided. Generating a random placeholder UUID for the recipient: {}. This may need to be updated later.", recipientUuid);
                } else {
                    // No keys at all, this is an error
                    log.error("Could not extract recipient info from keys. At least one key is required, and a receive key is preferred to identify the recipient.");
                    return false;
                }
            }

            // Check for duplicate, prevent creating multiple chats with the same recipient UUID for database integrity
            String finalRecipientUuid = recipientUuid;
            boolean exists = userChats.stream().anyMatch(c -> c.recipientUuid.equals(finalRecipientUuid));
            if (exists) {
                log.error("Chat with {} (UUID: {}) already exists", recipientName, finalRecipientUuid);
                return false;
            }

            // Create and add new chat state, in-memory info for the chats
            ChatState chat = new ChatState(recipientName, recipientUuid, sendSecretKey, sendIdx, sendTag, recvSecretKey, recvIdx, recvTag);

            userChats.add(chat);

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

    //  Retrieves the names of the chats for the GUI
    public List<String> getChatNames() {
        List<String> names = new ArrayList<>();
        names.add("+ New Chat (BUMP)");
        userChats.forEach(chat -> names.add(chat.toString()));
        return names;
    }

    public void renameChat(int listIndex, String newName) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= userChats.size()) return;

        ChatState chat = userChats.get(idx);
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

    // Send a message in the specified chat
    public void sendMessage(int chatIndex, String message) {
        // chatIndex is 1-based from the GUI, adjust to 0-based, this comes from the creation new chat being index 0
        chatIndex -= 1;
        if (chatIndex < 0 || chatIndex >= userChats.size()) {
            log.error("No valid chat selected for sending message.");
            return;
        }

        ChatState chat = userChats.get(chatIndex);

        if (!chat.canSend()) {
            log.error("Cannot send - this is a receive-only chat with {}", chat.recipient);
            return;
        }

        try {
            // add it locally to the chat state and database as PENDING
            chat.addSentMessage(message, currentUser);
            notifyMessageUpdate(); // notify UI of message update


            // save it in the db as pending and send it
            if (databaseManager != null) {
                // store message in the database
                long messageId = databaseManager.addMessage(chat.recipient, chat.getRecipientUuid(), message, true, false);
                // initiate sending immediately in a separate thread, for faster UI response
                DatabaseManager.PendingMessage pendingMessage = new DatabaseManager.PendingMessage(messageId, chat.recipient, chat.getRecipientUuid(), message, null, null, null);
                new Thread(() -> inAndOutBox.sendMessageImmediately(pendingMessage)).start();
            }
            log.info("Bericht lokaal gebufferd voor verzending naar {}", chat.recipient);
        } catch (Exception e) {
            log.error("Exception while buffering message", e);
        }
    }

    // get the chatstate by its index in the list (1-based, as shown in GUI)
    public ChatState getChatState(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= userChats.size()) {
            return null;
        }
        return userChats.get(idx);
    }

    // get messages for a chat by its index in the list
    public List<Message> getMessagesForChat(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= userChats.size()) {
            return java.util.Collections.emptyList();
        }
        ChatState chat = userChats.get(idx);
        return databaseManager.loadMessages(chat.recipient, chat.recipientUuid);
    }

    // check if we can send messages to this chat by its index in the list
    public boolean canSendToChat(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= userChats.size()) return false;
        return userChats.get(idx).canSend();
    }

    // get the chat state by recipient uuid
    public Optional<ChatState> getChatStateByRecipientUuid(String recipientUuid) {
        return userChats.stream().filter(c -> c.recipientUuid.equals(recipientUuid)).findFirst();
    }


    // gives a snapshot of the active chat states. Used by the outbox
    public List<ChatState> getActiveChatsSnapshot() {
        synchronized (userChats) {
            return new ArrayList<>(userChats);
        }
    }

    public void setActiveChatUuid(String chatUuid) {
        this.activeChatUuid = chatUuid;
        log.debug("Active chat set to: {}", chatUuid);
    }

    public String getActiveChatUuid() {
        return activeChatUuid;
    }

    public Optional<ChatState> getActiveChatState() {
        String uuid = activeChatUuid;
        if (uuid == null) return Optional.empty();
        return getChatStateByRecipientUuid(uuid);
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
