package org.example;

import com.google.protobuf.ByteString;
import org.example.controller.ChatCore;
import org.example.cypto.ChatCrypto;
import org.example.proto.ChatProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Optional;

/**
 * Processes locally buffered messages (Outbox) and retrieves new messages (Inbox).
 * Handles retry logic and the connection with the BulletinBoard.
 */
public class InAndOutBox implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(InAndOutBox.class);
    private final ChatCore chatCore;
    private BulletinBoard bulletinBoard = null;
    private final DatabaseManager databaseManager;
    private volatile boolean running = false;
    private long currentSleepTime = 1000; // Start with 1 second

    public InAndOutBox(ChatCore chatCore, DatabaseManager databaseManager) {
        this.chatCore = chatCore;
        this.databaseManager = databaseManager;
    }

    public void start() {
        if (running) return;
        running = true;
        Thread thread = new Thread(this, "Outbox-Inbox-Processor-Thread");
        thread.setDaemon(true);
        thread.start();
        log.info("Message processor started.");
    }

    public void stop() {
        running = false;
        log.info("Message processor stopped.");
    }

    @Override
    public void run() {
        while (running) {
            boolean anySuccess = false;

            try {
                // 1. Try to process the Outbox first (sending)
                boolean sentSuccessfully = processOutbox();
                if (sentSuccessfully) {
                    anySuccess = true;
                }
            } catch (Exception e) {
                log.error("Error during Outbox processing", e);
            }

            try {
                // 2. Then process the Inbox (receiving)
                boolean receivedSuccessfully = processInbox();
                if (receivedSuccessfully) {
                    anySuccess = true;
                }
            } catch (Exception e) {
                log.error("Error during Inbox processing", e);
            }

            Random random = new Random();
            int baseSleep = 1000;
            int maxSleep = 4000;

            if (anySuccess) {
                currentSleepTime = baseSleep;
            } else {
                long newSleep = Math.min(currentSleepTime * 3, maxSleep);
                currentSleepTime = random.nextLong(newSleep + 1); // 0 → newSleep
            }

            try {
                Thread.sleep(currentSleepTime);
            } catch (InterruptedException e) {
                // Thread interrupted, stop the loop
                running = false;
                break;
            }
        }
    }

    private boolean connectToBulletinBoard() {
        if (this.bulletinBoard != null) return true; // Already connected

        try {
            // Perform the RMI lookup
            Registry locateRegistry = LocateRegistry.getRegistry();
            this.bulletinBoard = (BulletinBoard) locateRegistry.lookup("BulletinBoard");
            log.info("RMI CONNECTION SUCCESS: BulletinBoard found and connected.");
            return true;
        } catch (RemoteException | NotBoundException e) {
            // Catches ConnectException (connection error) & NotBoundException (service not started)
            log.warn("RMI CONNECTION ERROR: BulletinBoard not reachable or not bound. Will retry later.");
            this.bulletinBoard = null;
            return false;
        }
    }

    /**
     * Retrieves buffered messages from the database and attempts to send them.
     * @return true if at least one message was successfully sent, otherwise false.
     */
    private boolean processOutbox() throws Exception {
        if (databaseManager == null) return false;

        // Check if connection is active
        if (!connectToBulletinBoard()) {
            return false;
        }

        boolean messageSent = false;

        // Retrieve all messages where 'is_sent=true' and 'is_server_sent=false'
        List<DatabaseManager.PendingMessage> pendingMessages = databaseManager.getPendingOutboxMessages();

        for (DatabaseManager.PendingMessage pending : pendingMessages) {

            Optional<ChatState> chatOptional = chatCore.getChatStateByRecipient(pending.recipient());
            if (chatOptional.isEmpty()) {
                log.error("Chat state not found for pending message to {}", pending.recipient());
                continue;
            }
            ChatState chat = chatOptional.get();

            try {
                // 1. Generate new state (idx', tag')
                long nextIdx = ChatCrypto.makeNewIdx();
                byte[] nextTagBytes = ChatCrypto.makeNewTag();
                String nextTag = ChatCrypto.tagToBase64(nextTagBytes);

                // 2. Build payload (with the stored text)
                ChatProto.ChatPayload chatPayload = ChatProto.ChatPayload.newBuilder()
                        .setMessage(pending.messageText())
                        .setNextIdx(nextIdx)
                        .setNextTag(ByteString.copyFrom(nextTagBytes))
                        .build();
                byte[] payloadBytes = chatPayload.toByteArray();

                // 3. Encrypt payload using the *current* sendKey
                byte[] encryptedPayload = ChatCrypto.encryptPayloadBytes(payloadBytes, chat.sendKey);

                // 4. Hash the current tag (needed by the server)
                String tagString = Encryption.preimageToTag(chat.sendTag);

                log.info("OUTBOX PUSH: Trying to send to {} at idx {} with tag {}",
                        pending.recipient(), chat.sendIdx, tagString);

                // 5. SERVER SEND (RMI Call)
                this.bulletinBoard.add((int) chat.sendIdx, encryptedPayload, tagString);
                log.info("OUTBOX PUSH SUCCESS: Message for {} sent.", pending.recipient());

                // 6. UPDATE LOCAL STATE (ROTATION)
                chat.sendIdx = nextIdx;
                chat.sendTag = nextTag;
                chat.sendKey = ChatCrypto.makeNewSecretKey(chat.sendKey);

                // 7. PERSISTENCE: Mark message as sent and update state
                databaseManager.markMessageAsSent(pending.id());

                byte[] sendKeyBytes = chat.sendKey.getEncoded();
                byte[] recvKeyBytes = chat.recvKey == null ? null : chat.recvKey.getEncoded();
                databaseManager.upsertChatState(chat.recipient, sendKeyBytes, recvKeyBytes,
                        chat.sendIdx, chat.recvIdx, chat.sendTag, chat.recvTag);

                messageSent = true;

            } catch (RemoteException e) {
                log.warn("RMI ERROR during outbox push. Server may be offline. Will retry later.");
                this.bulletinBoard = null;
                return messageSent;
            }
        }
        return messageSent;
    }

    /**
     * Checks all active chats for incoming messages from the BulletinBoard.
     * @return true if at least one new message was successfully received, otherwise false.
     */
    private boolean processInbox() throws Exception {
        if (databaseManager == null) return false;

        // Check if connection is active
        if (!connectToBulletinBoard()) {
            return false;
        }

        boolean messageReceived = false;
        boolean rmiFailed = false;

        // Get snapshot of active chats
        List<ChatState> chatsSnapshot = chatCore.getActiveChatsSnapshot();

        for (ChatState chat : chatsSnapshot) {

            // Only receive if state has a receiveKey
            if (!chat.canReceive()) {
                continue;
            }

            // Loop while messages exist on current index
            while (true) {
                try {
                    log.info("INBOX FETCH: Trying to receive for {} at recvIdx={} with tag={}",
                            chat.recipient, chat.recvIdx, chat.recvTag);

                    // 1. SERVER REQUEST (RMI Call)
                    Pair pair = this.bulletinBoard.get((int) chat.recvIdx, chat.recvTag);

                    if (pair == null) {
                        log.info("INBOX FETCH: No message found for {}", chat.recipient);
                        break;
                    }

                    log.info("INBOX FETCH: Message found for {}!", chat.recipient);

                    // 2. DECRYPTION
                    byte[] payloadBytes = ChatCrypto.decryptPayloadBytes(pair.value(), chat.recvKey);
                    ChatProto.ChatPayload chatPayload = ChatProto.ChatPayload.parseFrom(payloadBytes);

                    // 3. SAVE & ROTATE STATE
                    String receivedMessage = chatPayload.getMessage();
                    long nextIdx = chatPayload.getNextIdx();
                    byte[] nextTagBytes = chatPayload.getNextTag().toByteArray();
                    String nextTag = ChatCrypto.tagToBase64(nextTagBytes);

                    chat.addReceivedMessage(receivedMessage);

                    chat.recvIdx = nextIdx;
                    chat.recvTag = nextTag;
                    chat.recvKey = ChatCrypto.makeNewSecretKey(chat.recvKey);

                    // 4. PERSISTENCE
                    databaseManager.addMessage(chat.recipient, receivedMessage, false, true);

                    byte[] sendKeyBytes = chat.sendKey == null ? null : chat.sendKey.getEncoded();
                    byte[] recvKeyBytes = chat.recvKey.getEncoded();
                    databaseManager.upsertChatState(chat.recipient, sendKeyBytes, recvKeyBytes,
                            chat.sendIdx, chat.recvIdx, chat.sendTag, chat.recvTag);

                    messageReceived = true;

                } catch (RemoteException e) {
                    log.warn("RMI ERROR during inbox fetch for {}. Server unavailable. Stopping processing.", chat.recipient);
                    this.bulletinBoard = null;
                    rmiFailed = true;
                    break;
                } catch (Exception e) {
                    log.error("Error during decryption or processing of received message for {}", chat.recipient, e);
                    break;
                }
            }

            if (rmiFailed) {
                break;
            }
        }

        if (messageReceived) {
            chatCore.notifyMessageUpdate();
        }

        return messageReceived;
    }
}
