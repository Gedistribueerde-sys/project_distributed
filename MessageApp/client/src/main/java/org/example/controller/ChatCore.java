package org.example.controller;

import com.google.protobuf.ByteString;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.example.*;
import org.example.GUI.Message;
import org.example.cypto.ChatCrypto;
import org.example.cypto.KeyStoreImpl;
import org.example.proto.ChatProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ChatCore {
    private static final Logger log = LoggerFactory.getLogger(ChatCore.class);
    private final List<ChatState> activeChats = new ArrayList<>();
    private final KeyStoreImpl keyStore = new KeyStoreImpl();
    private String currentUser;

    private volatile boolean isFetching = false;
    private Thread autoFetchThread;
    private Runnable onMessageUpdate;

    private final BulletinBoard bulletinBoard;
    private DatabaseManager databaseManager;

    private final BooleanProperty loggedIn = new SimpleBooleanProperty(false);
    private final BooleanProperty loggedOut = new SimpleBooleanProperty(false);

    public ChatCore(BulletinBoard bulletinBoard) {this.bulletinBoard = bulletinBoard;}

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
                log.info("Database initialized for new user: {}", username);
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

            // Restore chat states from database
            restoreChatStates();

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

    public void logout() {
        stopAutoFetch();
        log.info("User {} logged out.", currentUser);
        currentUser = null;
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

                ChatState chat = new ChatState(
                        state.recipient(),
                    sendKey, state.sendNextIdx(), state.sendTag(),
                    recvKey, state.recvNextIdx(), state.recvTag()
                );

                // Load messages for this chat
                List<Message> messages = databaseManager.loadMessages(state.recipient());
                for (Message msg : messages) {
                    chat.getMessages().add(msg);
                }

                activeChats.add(chat);
                log.info("Restored chat with {}: {} message(s), sendTag={}, recvTag={}",
                        state.recipient(), messages.size(), state.sendTag(), state.recvTag());
            }
        } catch (Exception e) {
            log.error("Failed to restore chat states", e);
        }
    }

    // Generate a send key encoded as protobuf Base64 string
    // This key can be given to another user so they can receive messages
    public String generateSendKeyInfo() throws Exception {
        ChatProto.KeyInfo keyInfo = ChatCrypto.generateBumpKeyInfo();

        byte[] serialized = keyInfo.toByteArray();
        return Base64.getEncoder().encodeToString(serialized);
    }

    // Create a new chat with optional send and receive keys
    public boolean createChatWithKeys(String recipientName, String sendKeyString, String receiveKeyString) {
        try {
            // Check for duplicate
            boolean exists = activeChats.stream()
                    .anyMatch(c -> c.recipient.equals(recipientName));
            if (exists) {
                log.error("Chat with {} already exists", recipientName);
                return false;
            }

            // At least one key must be present
            if ((sendKeyString == null || sendKeyString.isEmpty()) &&
                (receiveKeyString == null || receiveKeyString.isEmpty())) {
                log.error("At least one key (send or receive) must be provided");
                return false;
            }

            SecretKey sendSecretKey = null;
            long sendIdx = 0;
            String sendTag = null;

            SecretKey recvSecretKey = null;
            long recvIdx = 0;
            String recvTag = null;

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

            // Parse receive key if present
            if (receiveKeyString != null && !receiveKeyString.trim().isEmpty()) {
                byte[] decoded = Base64.getDecoder().decode(receiveKeyString.trim());
                ChatProto.KeyInfo keyInfo = ChatProto.KeyInfo.parseFrom(decoded);

                byte[] keyBytes = keyInfo.getKey().toByteArray();
                recvSecretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
                recvIdx = keyInfo.getIdx();
                recvTag = Base64.getEncoder().encodeToString(keyInfo.getTag().toByteArray());

                log.info("Parsed receive key: idx={}, tag={}", recvIdx, recvTag);
            }

            ChatState chat = new ChatState(
                    recipientName,
                    sendSecretKey, sendIdx, sendTag,
                    recvSecretKey, recvIdx, recvTag
            );

            activeChats.add(chat);

            // Persist to database
            if (databaseManager != null) {
                byte[] sendKeyBytes = sendSecretKey == null ? null : sendSecretKey.getEncoded();
                byte[] recvKeyBytes = recvSecretKey == null ? null : recvSecretKey.getEncoded();
                databaseManager.upsertChatState(recipientName, sendKeyBytes, recvKeyBytes, sendIdx, recvIdx,
                    sendTag, recvTag);
            }

            log.info("Created chat with {}: canSend={}, canReceive={}",
                    recipientName, chat.canSend(), chat.canReceive());
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
        names.add("➕ New Chat (BUMP)");
        activeChats.forEach(chat -> names.add(chat.toString()));
        return names;
    }

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
            // 1. Generate new idx' and tag'
            long nextIdx = ChatCrypto.makeNewIdx();
            byte[] nextTagBytes = ChatCrypto.makeNewTag();
            String nextTag = ChatCrypto.tagToBase64(nextTagBytes);

            // 2. Build protobuf message
            ChatProto.ChatPayload chatPayload = ChatProto.ChatPayload.newBuilder()
                    .setMessage(message)
                    .setNextIdx(nextIdx)
                    .setNextTag(ByteString.copyFrom(nextTagBytes))
                    .build();

            byte[] payloadBytes = chatPayload.toByteArray();

            // 3. Encrypt payload to bytes
            byte[] encryptedPayload = ChatCrypto.encryptPayloadBytes(payloadBytes, chat.sendKey);

            // 4. Hash current tag (which is a Base64 string) to get the tag for the server
            String tagString = Encryption.preimageToTag(chat.sendTag);

            log.info("SEND: currentTag(base64)={}, tagHash={}", chat.sendTag, tagString);

            // 5. Send to server at current idx
            bulletinBoard.add((int) chat.sendIdx, encryptedPayload, tagString);

            log.info("Sent encrypted message at idx {} with tag {}", chat.sendIdx, tagString);

            // 6. Update local state to idx', tag', key'
            chat.sendIdx = nextIdx;
            chat.sendTag = nextTag;
            chat.sendKey = ChatCrypto.makeNewSecretKey(chat.sendKey);

            chat.addSentMessage(message, currentUser);

            // 7. Persist message and updated state to database
            if (databaseManager != null) {
                databaseManager.addMessage(chat.recipient, message, true);
                byte[] sendKeyBytes = chat.sendKey.getEncoded();
                byte[] recvKeyBytes = chat.recvKey == null ? null : chat.recvKey.getEncoded();
                databaseManager.upsertChatState(chat.recipient, sendKeyBytes, recvKeyBytes,
                    chat.sendIdx, chat.recvIdx, chat.sendTag, chat.recvTag);
            }
        } catch (Exception e) {
            log.error("Exception while sending message", e);
        }
    }
    public void setOnMessageUpdate(Runnable onMessageUpdate) {
        this.onMessageUpdate = onMessageUpdate;
    }
    public void startAutoFetch() {
        if (isFetching) return;
        isFetching = true;

        autoFetchThread = new Thread(() -> {
            log.info("Auto-fetch thread started.");
            long currentSleepTime = 10; // Start with 10ms

            while (isFetching) {
                boolean anyNewMessageFound = false;

                // Create a copy to avoid ConcurrentModificationException if user adds chat while fetching
                List<ChatState> chatsSnapshot;
                synchronized (activeChats) {
                    chatsSnapshot = new ArrayList<>(activeChats);
                }

                for (int i = 0; i < chatsSnapshot.size(); i++) {
                    ChatState chat = chatsSnapshot.get(i);

                    // Only attempt fetch if we can receive
                    if (chat.canReceive()) {
                        int preFetchSize = chat.getMessages().size();
                        try {
                            // listIndex is i + 1 because 0 is reserved for "New Chat" in your logic
                            fetchMessages(i + 1);
                        } catch (RemoteException e) {
                            log.error("Auto-fetch connection error", e);
                        }
                        int postFetchSize = chat.getMessages().size();

                        if (postFetchSize > preFetchSize) {
                            anyNewMessageFound = true;
                        }
                    }
                }

                if (anyNewMessageFound) {
                    // Reset backoff to fast polling
                    currentSleepTime = 10;

                    // Notify UI to refresh (if callback is set)
                    if (onMessageUpdate != null) {
                        onMessageUpdate.run();
                    }
                } else {
                    // Backoff Strategy
                    if (currentSleepTime == 10) {
                        currentSleepTime =  250;
                    } else if (currentSleepTime == 250) {
                        currentSleepTime = 500;
                    } else if (currentSleepTime == 500) {
                        currentSleepTime = 2000; // Jump to 2 seconds
                    } else if (currentSleepTime == 2000) {
                        currentSleepTime = 4000; // Max out at 4 seconds
                    }else{
                            currentSleepTime = 4000; //  Max out at 4 seconds

                    }
                }

                try {
                    Thread.sleep(currentSleepTime);
                } catch (InterruptedException e) {
                    log.info("Auto-fetch interrupted, stopping.");
                    break;
                }
            }
        });

        autoFetchThread.setDaemon(true); // Ensure thread dies if app closes
        autoFetchThread.start();
    }

    public void stopAutoFetch() {
        isFetching = false;
        if (autoFetchThread != null) {
            autoFetchThread.interrupt();
        }
        log.info("Auto-fetch stopped.");
    }
    public boolean fetchMessages(int listIndex) throws RemoteException {

        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) {
            log.error("No valid chat selected for fetching messages.");
            return false;
        }

        ChatState chat = activeChats.get(idx);

        if (!chat.canReceive()) {
            log.error("Cannot receive - this is a send-only chat with {}", chat.recipient);
            return false;
        }

        while (true) {
            log.info("FETCH: recvIdx={}, recvTag(base64)={}", chat.recvIdx, chat.recvTag);

            Pair pair = bulletinBoard.get((int) chat.recvIdx, chat.recvTag);
            if (pair == null) {
                log.info("FETCH: No message found");
                break;
            }

            log.info("FETCH: Found message!");

            try {
                byte[] payloadBytes = ChatCrypto.decryptPayloadBytes(pair.value(), chat.recvKey);
                ChatProto.ChatPayload chatPayload = ChatProto.ChatPayload.parseFrom(payloadBytes);

                String receivedMessage = chatPayload.getMessage();
                long nextIdx = chatPayload.getNextIdx();
                byte[] nextTagBytes = chatPayload.getNextTag().toByteArray();
                String nextTag = ChatCrypto.tagToBase64(nextTagBytes);

                log.info("Received message: {}", receivedMessage);

                chat.addReceivedMessage(receivedMessage);

                chat.recvIdx = nextIdx;
                chat.recvTag = nextTag;
                chat.recvKey = ChatCrypto.makeNewSecretKey(chat.recvKey);

                // Persist received message and updated state to database
                if (databaseManager != null) {
                    databaseManager.addMessage(chat.recipient, receivedMessage, false);
                    byte[] sendKeyBytes = chat.sendKey == null ? null : chat.sendKey.getEncoded();
                    byte[] recvKeyBytes = chat.recvKey.getEncoded();
                    databaseManager.upsertChatState(chat.recipient, sendKeyBytes, recvKeyBytes,
                        chat.sendIdx, chat.recvIdx, chat.sendTag, chat.recvTag);
                }

            } catch (Exception e) {
                log.error("Exception while decrypting message", e);
                break;
            }
        }

        return true;
    }

    public List<Message> getMessagesForChat(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) {
            return java.util.Collections.emptyList();
        }
        return activeChats.get(idx).getMessages();
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

}
