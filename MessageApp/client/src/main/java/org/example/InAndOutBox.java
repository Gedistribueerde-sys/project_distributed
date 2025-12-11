package org.example;

import com.google.protobuf.ByteString;
import org.example.controller.ChatCore;
import org.example.crypto.ChatCrypto;
import org.example.proto.ChatProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import java.util.concurrent.locks.ReentrantLock;

// Processes outgoing and incoming messages using RMI to communicate with BulletinBoard servers.
public class InAndOutBox implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(InAndOutBox.class);
    private final ChatCore chatCore;
    private final DatabaseManager databaseManager;
    private volatile boolean running = false;
    private Thread thread;
    private final Random random = new Random();
    private final ReentrantLock outboxLock = new ReentrantLock();
    private final ReentrantLock inboxLock = new ReentrantLock();

    private static final int[] RMI_PORTS = {1099, 1100};
    private static final int NUM_SERVERS = RMI_PORTS.length;

    // Cache for RMI stubs
    private final Map<String, BulletinBoard> bulletinBoardStubs = new HashMap<>();

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

    // Stops the message processing thread gracefully.
    public void stop() {
        running = false;
        disconnect();
        if (thread != null) {
            thread.interrupt();
        }
        log.info("Message processor stopping.");
    }
    
    // Waits for the processing thread to finish.
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

    // Exponential backoff loop for processing outbox messages.
    private void processOutbox() {
        long currentBackoff = 1000;
        final int maxBackoff = 8000;

        while (running) {
            if (!processOneOutboxMessageSafely()) {
                long sleepTime = currentBackoff + random.nextInt(1000);
                currentBackoff = Math.min(currentBackoff * 2, maxBackoff);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                currentBackoff = 1000; // Reset backoff on success
            }
        }
    }

    // Exponential backoff loop for processing background inbox messages.
    private void processInbox() {
        long currentBackoff = 1000;
        final int maxBackoff = 8000;

        while (running) {
            if (!processBackgroundInboxMessages()) {
                long sleepTime = currentBackoff + random.nextInt(1000);
                currentBackoff = Math.min(currentBackoff * 2, maxBackoff);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                currentBackoff = 1000; // Reset backoff on success
            }
        }
    }

    // Low-latency loop for processing the active inbox.
    private void processActiveInbox() {
        final int baseInterval = 300;  // Base polling interval in ms
        final int maxJitter = 200;     // Maximum jitter in ms

        while (running) {
            processActiveChatMessage();

            // Low latency with small jitter
            int sleepTime = baseInterval + random.nextInt(maxJitter);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Processes messages for the currently active chat.
    private void processActiveChatMessage() {
        if (databaseManager == null) return;

        Optional<ChatState> activeChatOpt = chatCore.getActiveChatState();
        if (activeChatOpt.isEmpty()) return;

        ChatState activeChat = activeChatOpt.get();
        if (!activeChat.canReceive() || activeChat.isPoisoned()) return;

        // Try to lock, if busy skip this cycle
        if (!inboxLock.tryLock()) return;

        try {
            fetchAndProcessMessage(activeChat);
        } finally {
            inboxLock.unlock();
        }
    }

    // Processes inbox messages for all non-active chats.
    private boolean processBackgroundInboxMessages() {
        if (databaseManager == null) return false;

        if (!inboxLock.tryLock()) return false;

        try {
            String activeChatUuid = chatCore.getActiveChatUuid();
            boolean didWork = false;

            for (ChatState chat : chatCore.getActiveChatsSnapshot()) {
                // Skip the active chat (handled by fast polling)
                if (chat.getRecipientUuid().equals(activeChatUuid)) {
                    continue;
                }

                if (chat.canReceive() && !chat.isPoisoned()) {
                    if (fetchAndProcessMessage(chat)) {
                        didWork = true;
                    }
                }
            }
            return didWork;
        } finally {
            inboxLock.unlock();
        }
    }

    // Clears all RMI connections.
    private void disconnect() {
        bulletinBoardStubs.clear();
        log.info("All RMI connections disconnected.");
    }

    // Main processing loop with exponential backoff for confirmations.
    @Override
    public void run() {
        long currentBackoff = 1000;
        final int maxBackoff = 8000;
        final int baseSleep = 500;

        new Thread(this::processOutbox, "Outbox-Processor-Thread").start();
        new Thread(this::processInbox, "Background-Inbox-Processor-Thread").start();
        new Thread(this::processActiveInbox, "Active-Inbox-Processor-Thread").start();

        while (running) {
            boolean didWork = false;

            if (!running) break;
            didWork = processOneConfirmationSafely();

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

    // Ensures there is an RMI connection to the server responsible for the given index.
    private Optional<BulletinBoard> ensureConnected(long requiredIndex) {
        int targetPort = getPortForIndex(requiredIndex);
        String targetHost = "localhost";
        String targetHostPort = targetHost + ":" + targetPort;

        // If we have a cached stub, return it.
        if (bulletinBoardStubs.containsKey(targetHostPort)) {
            return Optional.of(bulletinBoardStubs.get(targetHostPort));
        }

        // If not connected, establish connection
        try {
            Registry registry = LocateRegistry.getRegistry(targetHost, targetPort);
            BulletinBoard bulletinBoard = (BulletinBoard) registry.lookup("BulletinBoard");
            bulletinBoardStubs.put(targetHostPort, bulletinBoard);
            log.info("RMI CONNECTION SUCCESS: BulletinBoard found and connected on: {}.", targetHostPort);
            return Optional.of(bulletinBoard);
        } catch (RemoteException | NotBoundException e) {
            log.warn("RMI CONNECTION ERROR: BulletinBoard on {} not accessible or found, trying again later.", targetHostPort);
            return Optional.empty();
        }
    }

    // Immediately sends a pending message, bypassing the usual scheduling.
    public void sendMessageImmediately(DatabaseManager.PendingMessage pending) {
        outboxLock.lock();
        try {
            processMessage(pending);
        } finally {
            outboxLock.unlock();
        }
    }

    // Processes one outbox message safely with locking.
    private boolean processOneOutboxMessageSafely() {
        if (databaseManager == null) return false;

        Optional<DatabaseManager.PendingMessage> pendingMessageOpt = databaseManager.getPendingOutboxMessages().stream().findFirst();
        if (pendingMessageOpt.isEmpty()) return false;

        if (!outboxLock.tryLock()) return false;

        try {
            return processMessage(pendingMessageOpt.get());
        } finally {
            outboxLock.unlock();
        }
    }

    // Processes a single pending outbox message.
    private boolean processMessage(DatabaseManager.PendingMessage pending) {
        Optional<ChatState> chatOptional = chatCore.getChatStateByRecipientUuid(pending.recipientUuid());
        if (chatOptional.isEmpty()) {
            log.error("Chat state not found for pending message to {}", pending.recipient());
            return false;
        }
        ChatState chat = chatOptional.get();

        Optional<BulletinBoard> bulletinBoardOpt = ensureConnected(chat.sendIdx);
        if (bulletinBoardOpt.isEmpty()) return false;

        BulletinBoard bulletinBoard = bulletinBoardOpt.get();

        try {
            // Two-Phase Send Logic for Idempotent Retries:
            // Phase 1: Ensure proposed values exist (generate and persist if not)
            long nextIdx;
            byte[] nextTagBytes;
            String nextTag;
            byte[] nextKeyBytes;

            if (pending.proposedNextIdx() != null && pending.proposedNextTag() != null && pending.proposedNextKey() != null) {
                // Use existing proposed values (retry scenario)
                nextIdx = pending.proposedNextIdx();
                nextTag = pending.proposedNextTag();
                nextTagBytes = java.util.Base64.getDecoder().decode(nextTag);
                nextKeyBytes = pending.proposedNextKey();
                log.info("Using existing proposed values for message {}: idx={}", pending.id(), nextIdx);
            } else {
                // Generate new proposed values and persist them first
                nextIdx = ChatCrypto.makeNewIdx();
                nextTagBytes = ChatCrypto.makeNewTag();
                nextTag = ChatCrypto.tagToBase64(nextTagBytes);
                nextKeyBytes = ChatCrypto.makeNewSecretKey(chat.sendKey).getEncoded();

                // Persist proposed values BEFORE attempting to send
                databaseManager.saveProposedSendValues(pending.id(), pending.recipientUuid(), nextIdx, nextTag, nextKeyBytes);
                log.info("Generated and saved proposed values for message {}: idx={}", pending.id(), nextIdx);
            }

            // Phase 2: Construct and send the payload using the stored proposed values
            ChatProto.ChatPayload chatPayload = ChatProto.ChatPayload.newBuilder()
                    .setMessage(pending.messageText())
                    .setNextIdx(nextIdx)
                    .setNextTag(ByteString.copyFrom(nextTagBytes))
                    .build();
            byte[] payloadBytes = chatPayload.toByteArray();

            byte[] encryptedPayload = ChatCrypto.encryptPayloadBytes(payloadBytes, chat.sendKey);
            String tagString = Encryption.preimageToTag(chat.sendTag);

            // Compute proof-of-work before sending
            log.info("OUTBOX PUSH: Computing proof-of-work for message to {} at idx {}", pending.recipient(), chat.sendIdx);
            ProofOfWork.ProofResult powResult = ProofOfWork.computeProof(tagString, chat.sendIdx);
            log.info("OUTBOX PUSH: Proof-of-work computed in {}ms (nonce={})", powResult.computationTimeMs(), powResult.nonce());

            log.info("OUTBOX PUSH: Trying to send to {} at idx {} with tag {}", pending.recipient(), chat.sendIdx, tagString);

            boolean success = bulletinBoard.add(chat.sendIdx, encryptedPayload, tagString, powResult.nonce());

            if (!success) {
                log.warn("OUTBOX PUSH FAILED: Server returned false. Will retry later.");
                return false;
            }

            log.info("OUTBOX PUSH SUCCESS: Message for {} sent.", pending.recipient());

            // Phase 3: Finalize - move proposed values to actual state
            chat.sendIdx = nextIdx;
            chat.sendTag = nextTag;
            chat.sendKey = new javax.crypto.spec.SecretKeySpec(nextKeyBytes, "AES");

            databaseManager.markMessageAsSentAndUpdateState(pending.id(), chat.recipient, nextKeyBytes, chat.sendIdx, chat.sendTag);

            // Notify UI to refresh and update the message status icon from pending to sent
            chatCore.notifyMessageUpdate();

            return true;
        } catch (RemoteException e) {
            log.warn("RMI ERROR during outbox push. Server may be offline. Will retry later.", e);
            bulletinBoardStubs.clear();
            return false;
        } catch (Exception e) {
            log.error("Failed to process outbox message for {}", pending.recipient(), e);
            return false;
        }
    }

    // Immediately fetches messages for the given chat, bypassing the usual scheduling.
    public void fetchMessagesImmediately(ChatState chat) {
        if (!inboxLock.tryLock()) {
            log.warn("Skipping immediate fetch for {} as another fetch is in progress.", chat.recipient);
            return;
        }
        try {
            log.info("Starting immediate fetch for active chat: {}", chat.recipient);
            final int maxMessagesToFetch = 10;
            int fetchedCount = 0;
            // Keep fetching as long as we are making progress and within the limit
            while (fetchedCount < maxMessagesToFetch && fetchAndProcessMessage(chat)) {
                fetchedCount++;
                try {
                    // Small delay to avoid spamming the server
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("Finished immediate fetch for {}. Fetched {} messages.", chat.recipient, fetchedCount);
        } finally {
            inboxLock.unlock();
        }
    }

    // Fetches and processes a single message for the given chat.
    private boolean fetchAndProcessMessage(ChatState chat) {
        Optional<BulletinBoard> bulletinBoardOpt = ensureConnected(chat.recvIdx);
        if (bulletinBoardOpt.isEmpty()) return false;

        BulletinBoard bulletinBoard = bulletinBoardOpt.get();
        long currentRecvIdx = chat.recvIdx;
        String currentRecvTag = chat.recvTag;

        try {
            log.info("INBOX FETCH: Trying to receive for {} at recvIdx={} with tag={}", chat.recipient, currentRecvIdx, currentRecvTag);
            Pair pair = bulletinBoard.get(currentRecvIdx, currentRecvTag);

            if (pair == null) return false;

            log.info("INBOX FETCH: Message found for {}!", chat.recipient);

            byte[] payloadBytes = ChatCrypto.decryptPayloadBytes(pair.value(), chat.recvKey);
            ChatProto.ChatPayload chatPayload = ChatProto.ChatPayload.parseFrom(payloadBytes);

            String receivedMessage = chatPayload.getMessage();
            long nextIdx = chatPayload.getNextIdx();
            byte[] nextTagBytes = chatPayload.getNextTag().toByteArray();
            String nextTag = ChatCrypto.tagToBase64(nextTagBytes);

            chat.addReceivedMessage(receivedMessage);
            chat.recvIdx = nextIdx;
            chat.recvTag = nextTag;
            chat.recvKey = ChatCrypto.makeNewSecretKey(chat.recvKey);

            byte[] newRecvKeyBytes = chat.recvKey.getEncoded();
            databaseManager.addReceivedMessageAndUpdateState(chat.recipient, chat.getRecipientUuid(), receivedMessage, currentRecvIdx, pair.tag(), newRecvKeyBytes, chat.recvIdx, chat.recvTag);

            chatCore.notifyMessageUpdate();
            return true;

        } catch (RemoteException e) {
            log.warn("RMI ERROR during inbox fetch for {}. Server unavailable. Will retry later.", chat.recipient);
            bulletinBoardStubs.clear();
            return false;
        } catch (Exception e) {
            log.error("Error during decryption or processing of received message for {}", chat.recipient, e);
            chat.poisonedBackoffUntil = System.currentTimeMillis() + (5 * 60 * 1000);
            log.warn("Poison message detected for chat with {}. Backing off for 5 minutes.", chat.recipient);
            return true;
        }
    }

    // Processes one pending confirmation safely with locking.
    private boolean processOneConfirmationSafely() {
        if (databaseManager == null) {
            return false;
        }
        Optional<DatabaseManager.UnconfirmedMessage> unconfirmedOpt = databaseManager.getUnconfirmedMessages().stream().findFirst();
        if (unconfirmedOpt.isEmpty()) {
            return false; // No work to do
        }
        DatabaseManager.UnconfirmedMessage unconfirmed = unconfirmedOpt.get();

        Optional<BulletinBoard> bulletinBoardOpt = ensureConnected(unconfirmed.recvIdx());
        if (bulletinBoardOpt.isEmpty()) {
            return false; // Can't connect to server, will retry later
        }
        BulletinBoard bulletinBoard = bulletinBoardOpt.get();

        try {
            log.info("INBOX CONFIRM: Trying to confirm receipt for message with tag {}", unconfirmed.recvTag());
            boolean success = bulletinBoard.confirm(unconfirmed.recvIdx(), unconfirmed.recvTag());
            if (success) {
                databaseManager.deletePendingConfirmation(unconfirmed.messageId());
                log.info("INBOX CONFIRM: Successfully confirmed message with tag {}", unconfirmed.recvTag());
                return true; // We did work
            } else {
                log.warn("INBOX CONFIRM: Server returned false for tag {}. Will retry.", unconfirmed.recvTag());
                return false;
            }
        } catch (RuntimeException e) {
            log.warn("RMI ERROR during confirmation for tag {}. Will retry later.", unconfirmed.recvTag());
            bulletinBoardStubs.clear();
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during confirmation for tag {}", unconfirmed.recvTag(), e);
            return false;
        }
    }

    // Determines the RMI port for a given index.
    private int getPortForIndex(long idx) {
        int portIndex = (int) (Math.abs(idx) % NUM_SERVERS);
        return RMI_PORTS[portIndex];
    }
}
