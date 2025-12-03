package org.example;

import com.google.protobuf.ByteString;
import org.example.controller.ChatCore;
import org.example.cypto.ChatCrypto;
import org.example.proto.ChatProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Optional;
import java.util.Random;

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
    private Thread thread;
    private final Random random = new Random();

    public InAndOutBox(ChatCore chatCore, DatabaseManager databaseManager) {
        this.chatCore = chatCore;
        this.databaseManager = databaseManager;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "Outbox-Inbox-Processor-Thread");
        thread.start();
        log.info("Message processor started.");
    }

    /**
     * Signals the message processor to stop.
     * The shutdown is not immediate. If an RMI or database operation is in progress,
     * the thread will complete the current message processing before terminating.
     * This method interrupts the thread to wake it from sleep and relies on the
     * run loop checking the `running` flag.
     */
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
        log.info("Message processor stopping.");
    }

    /**
     * Waits for the message processing thread to terminate.
     * This should be called after `stop()` to ensure a graceful shutdown.
     */
    public void join() {
        try {
            if (thread != null) {
                thread.join();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for message processor to stop.", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        long currentBackoff = 1000;
        final int maxBackoff = 8000;
        final int baseSleep = 500;

        while (running) {
            // Check connection once per loop
            if (!ensureConnected()) {
                // If connection fails, apply exponential backoff and retry in the next loop
                long sleepTime = currentBackoff + random.nextInt(1000);
                currentBackoff = Math.min(currentBackoff * 2, maxBackoff);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    break;
                }
                continue; // Skip processing if not connected
            }

            boolean didWork = false;

            if (!running) break;
            didWork |= processOneOutboxMessageSafely();

            if (!running) break;
            didWork |= processOneInboxMessageSafely();

            long sleepTime;
            if (didWork) {
                sleepTime = baseSleep;
                currentBackoff = 1000; // Reset backoff on success
            } else {
                sleepTime = currentBackoff + random.nextInt(1000);
                currentBackoff = Math.min(currentBackoff * 2, maxBackoff);
            }

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private boolean ensureConnected() {
        if (this.bulletinBoard != null) return true; // Already connected

        try {
            Registry locateRegistry = LocateRegistry.getRegistry();
            this.bulletinBoard = (BulletinBoard) locateRegistry.lookup("BulletinBoard");
            log.info("RMI CONNECTION SUCCESS: BulletinBoard found and connected.");
            return true;
        } catch (RemoteException | NotBoundException e) {
            log.warn("RMI CONNECTION ERROR: BulletinBoard not reachable or not bound. Will retry later.");
            this.bulletinBoard = null;
            return false;
        }
    }

    private boolean processOneOutboxMessageSafely() {
        if (databaseManager == null) {
            return false;
        }

        // Retrieve the first pending message
        Optional<DatabaseManager.PendingMessage> pendingMessageOpt = databaseManager.getPendingOutboxMessages().stream().findFirst();
        if (pendingMessageOpt.isEmpty()) {
            return false;
        }
        DatabaseManager.PendingMessage pending = pendingMessageOpt.get();

        // Get chat state
        Optional<ChatState> chatOptional = chatCore.getChatStateByRecipient(pending.recipient());
        if (chatOptional.isEmpty()) {
            log.error("Chat state not found for pending message to {}", pending.recipient());
            return false;
        }
        ChatState chat = chatOptional.get();

        try {
            // 1. Generate new state (idx', tag')
            long nextIdx = ChatCrypto.makeNewIdx();
            byte[] nextTagBytes = ChatCrypto.makeNewTag();
            String nextTag = ChatCrypto.tagToBase64(nextTagBytes);

            // 2. Build payload
            ChatProto.ChatPayload chatPayload = ChatProto.ChatPayload.newBuilder()
                    .setMessage(pending.messageText())
                    .setNextIdx(nextIdx)
                    .setNextTag(ByteString.copyFrom(nextTagBytes))
                    .build();
            byte[] payloadBytes = chatPayload.toByteArray();

            // 3. Encrypt payload
            byte[] encryptedPayload = ChatCrypto.encryptPayloadBytes(payloadBytes, chat.sendKey);

            // 4. Hash the current tag
            String tagString = Encryption.preimageToTag(chat.sendTag);

            log.info("OUTBOX PUSH: Trying to send to {} at idx {} with tag {}",
                    pending.recipient(), chat.sendIdx, tagString);

            // 5. SERVER SEND (RMI Call)
            this.bulletinBoard.add(chat.sendIdx, encryptedPayload, tagString);
            log.info("OUTBOX PUSH SUCCESS: Message for {} sent.", pending.recipient());

            // 6. UPDATE LOCAL STATE & PERSIST
            // This part should be transactional if possible
            chat.sendIdx = nextIdx;
            chat.sendTag = nextTag;
            chat.sendKey = ChatCrypto.makeNewSecretKey(chat.sendKey);

            byte[] newSendKeyBytes = chat.sendKey.getEncoded();
            databaseManager.markMessageAsSentAndUpdateState(pending.id(), chat.recipient, newSendKeyBytes, chat.sendIdx, chat.sendTag);

            return true; // Work was done

        } catch (RemoteException e) {
            log.warn("RMI ERROR during outbox push. Server may be offline. Will retry later.");
            this.bulletinBoard = null; // Reset connection
            return false; // No work was done from a final point of view
        } catch (Exception e) {
            log.error("Failed to process outbox message for {}", pending.recipient(), e);
            return false;
        }
    }

    private boolean processOneInboxMessageSafely() {
        if (databaseManager == null) {
            return false;
        }

        // Find a chat that can receive messages and is not in a poison-message backoff period
        Optional<ChatState> chatToProcessOpt = chatCore.getActiveChatsSnapshot().stream()
                .filter(chat -> chat.canReceive() && !chat.isPoisoned())
                .findFirst();

        if (chatToProcessOpt.isEmpty()) {
            return false;
        }
        ChatState chat = chatToProcessOpt.get();

        try {
            log.info("INBOX FETCH: Trying to receive for {} at recvIdx={} with tag={}",
                    chat.recipient, chat.recvIdx, chat.recvTag);

            // 1. SERVER REQUEST (RMI Call)
            Pair pair = this.bulletinBoard.get(chat.recvIdx, chat.recvTag);

            if (pair == null) {
                // No message for this chat at this index
                return false;
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
            byte[] newRecvKeyBytes = chat.recvKey.getEncoded();
            databaseManager.addReceivedMessageAndUpdateState(chat.recipient, receivedMessage, newRecvKeyBytes, chat.recvIdx, chat.recvTag);

            chatCore.notifyMessageUpdate();
            return true; // Work was done

        } catch (RemoteException e) {
            log.warn("RMI ERROR during inbox fetch for {}. Server unavailable. Will retry later.", chat.recipient);
            this.bulletinBoard = null; // Reset connection
            return false;
        } catch (Exception e) {
            log.error("Error during decryption or processing of received message for {}", chat.recipient, e);

            // This is a poison message. To avoid getting stuck in a loop, put this chat in a temporary
            // backoff period (e.g., 5 minutes) before trying to process its inbox again.
            chat.poisonedBackoffUntil = System.currentTimeMillis() + (5 * 60 * 1000);
            log.warn("Poison message detected for chat with {}. Backing off for 5 minutes.", chat.recipient);

            return true; // Return true to indicate work was done (handling the poison pill)
        }
    }
}
