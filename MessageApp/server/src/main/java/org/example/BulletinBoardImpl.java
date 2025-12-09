package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BulletinBoardImpl implements BulletinBoard {
    private static final Logger logger = LoggerFactory.getLogger(BulletinBoardImpl.class);

    private static final double LOAD_FACTOR_THRESHOLD = 0.01;
    private final transient ServerDatabaseManager dbManager;

    private volatile BoardGeneration activeBoard;
    private final CopyOnWriteArrayList<BoardGeneration> drainingBoards = new CopyOnWriteArrayList<>();

    // --- Two-Phase Commit for Get ---
    private static class CheckedOutMessage {
        final BoardGeneration board;
        final long idx;
        final String tag;
        final byte[] value;
        final long timestamp = System.currentTimeMillis();

        CheckedOutMessage(BoardGeneration board, long idx, String tag, byte[] value) {
            this.board = board;
            this.idx = idx;
            this.tag = tag;
            this.value = value;
        }
    }
    private final Map<String, CheckedOutMessage> checkedOutMessages = new ConcurrentHashMap<>();
    private static final long CHECKOUT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);
    // --------------------------------

    public BulletinBoardImpl(ServerDatabaseManager dbManager) {
        this.dbManager = dbManager;

        // --- Database Recovery ---
        List<ServerDatabaseManager.PersistedMessage> allMessages = dbManager.loadAllMessagesWithCapacity();
        Map<Integer, List<ServerDatabaseManager.PersistedMessage>> messagesByCapacity = allMessages.stream()
                .collect(Collectors.groupingBy(ServerDatabaseManager.PersistedMessage::boardCapacity));

        if (messagesByCapacity.isEmpty()) {
            int initialSize = 1024;
            this.activeBoard = new BoardGeneration(initialSize);
            logger.info("No data in DB. Initialized new board size: {}", initialSize);
        } else {
            int activeCapacity = messagesByCapacity.keySet().stream().max(Integer::compare).get();

            Map<Integer, BoardGeneration> boardsByCapacity = new HashMap<>();
            for (Map.Entry<Integer, List<ServerDatabaseManager.PersistedMessage>> entry : messagesByCapacity.entrySet()) {
                int capacity = entry.getKey();
                List<ServerDatabaseManager.PersistedMessage> messages = entry.getValue();
                BoardGeneration board = new BoardGeneration(capacity);
                messages.forEach(board::loadMessage);
                boardsByCapacity.put(capacity, board);
            }

            this.activeBoard = boardsByCapacity.remove(activeCapacity);
            this.drainingBoards.addAll(boardsByCapacity.values());

            String drainingSizes = drainingBoards.stream().map(b -> String.valueOf(b.capacity)).collect(Collectors.joining(", "));
            logger.info("Server loaded. Active size: {}. Draining sizes: [{}]", this.activeBoard.capacity, drainingSizes.isEmpty() ? "None" : drainingSizes);
        }
    }

    @Override
    public boolean add(long idx, byte[] value, String tag) throws RemoteException {
        if (activeBoard.isOverloaded()) {
            checkAndResize();
        }
        return activeBoard.add(idx, value, tag, dbManager);
    }

    @Override
    public Pair get(long idx, String preimage) throws RemoteException {
        String tag = Encryption.preimageToTag(preimage);

        // Prevent processing a message that is already checked out
        if (checkedOutMessages.containsKey(tag)) {
            return null;
        }

        // Search draining boards first
        for (BoardGeneration board : drainingBoards) {
            Pair result = board.findAndRemoveFromBucket(idx, tag);
            if (result != null) {
                checkedOutMessages.put(tag, new CheckedOutMessage(board, idx, tag, result.value()));
                if (board.getTotalCount() == 0) {
                    drainingBoards.remove(board);
                }
                return result;
            }
        }

        // Search active board
        Pair result = activeBoard.findAndRemoveFromBucket(idx, tag);
        if (result != null) {
            checkedOutMessages.put(tag, new CheckedOutMessage(activeBoard, idx, tag, result.value()));
        }
        return result;
    }

    @Override
    public boolean confirm(long idx, String tag) throws RemoteException {
        logger.debug("CONFIRM received for tag: {}", tag);

        // Best-effort cleanup of in-memory state first.
        checkedOutMessages.remove(tag);

        // Also clean up from the main buckets in case this is a retry after a server crash.
        // This makes the confirm idempotent for the in-memory state.
        activeBoard.findAndRemoveFromBucket(idx, tag);
        drainingBoards.forEach(b -> b.findAndRemoveFromBucket(idx, tag));

        try {
            // The authoritative step: delete from durable storage.
            dbManager.deleteMessage(tag);
            logger.info("CONFIRMED and deleted message with tag: {}", tag);
            return true;
        } catch (Exception e) {
            // This can happen if confirm is called multiple times for the same tag.
            // We can consider it a success if the message is already gone.
            logger.warn("DB Delete failed on confirm for tag: {}. Assuming already deleted.", tag, e);
            return true;
        }
    }

    public void cleanUpOrphanedMessages() {
        long now = System.currentTimeMillis();
        List<String> orphanedTags = new ArrayList<>();

        for (CheckedOutMessage checkedOut : checkedOutMessages.values()) {
            if (now - checkedOut.timestamp > CHECKOUT_TIMEOUT_MS) {
                // Return the message to its original board
                checkedOut.board.putBack(checkedOut.idx, checkedOut.tag, checkedOut.value);
                orphanedTags.add(checkedOut.tag);
                logger.warn("TIMED OUT message with tag: {}. Returned to board.", checkedOut.tag);
            }
        }
        // Remove all orphaned messages from the checkout list
        orphanedTags.forEach(checkedOutMessages::remove);
    }


    private synchronized void checkAndResize() {
        if (activeBoard.isOverloaded()) {
            logger.info("RESIZING: Board full ({} items). expanding...", activeBoard.getTotalCount());
            drainingBoards.add(this.activeBoard);
            int newSize = this.activeBoard.capacity * 2;
            this.activeBoard = new BoardGeneration(newSize);
            logger.info("RESIZING: New Active Board created with size {}", newSize);
        }
    }

    private static class BoardGeneration {
        private final int capacity;
        private final List<Map<String, byte[]>> buckets;
        private final AtomicInteger totalItems = new AtomicInteger(0);

        public BoardGeneration(int size) {
            this.capacity = size;
            this.buckets = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                buckets.add(new ConcurrentHashMap<>());
            }
        }

        public void loadMessage(ServerDatabaseManager.PersistedMessage msg) {
            int index = msg.cellIndex();
            if (index >= 0 && index < this.capacity) {
                this.buckets.get(index).put(msg.messageTag(), msg.messageValue());
                this.totalItems.incrementAndGet();
            } else {
                logger.error("LOAD ERROR: Message tag {} stored with index {} does not fit in board size {}. Data integrity compromised.", msg.messageTag(), index, this.capacity);
            }
        }

        public void putBack(long idx, String tag, byte[] value) {
            int index = computeIndex(idx);
            Map<String, byte[]> cell = buckets.get(index);
            synchronized (cell) {
                // Put back and increment count
                cell.put(tag, value);
                totalItems.incrementAndGet();
            }
        }

        public boolean add(long idx, byte[] value, String tag, ServerDatabaseManager db) {
            int index = computeIndex(idx);
            Map<String, byte[]> cell = buckets.get(index);
            synchronized (cell) {
                if (cell.containsKey(tag)) return false;
                try {
                    db.saveMessage(index, this.capacity, tag, value);
                    cell.put(tag, value);
                    totalItems.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    logger.error("DB Save failed", e);
                    return false;
                }
            }
        }

        public Pair findAndRemoveFromBucket(long idx, String tag) {
            int index = computeIndex(idx);
            Map<String, byte[]> cell = buckets.get(index);
            byte[] value;
            synchronized (cell) {
                value = cell.remove(tag);
            }
            if (value != null) {
                // Decrement count only if found and removed
                totalItems.decrementAndGet();
                return new Pair(value, tag);
            }
            return null;
        }

        public boolean isOverloaded() {
            return totalItems.get() > (capacity * LOAD_FACTOR_THRESHOLD);
        }

        public int getTotalCount() {
            return totalItems.get();
        }

        private int computeIndex(long idx) {
            return (int) ((idx % capacity) + capacity) % capacity;
        }
    }
}