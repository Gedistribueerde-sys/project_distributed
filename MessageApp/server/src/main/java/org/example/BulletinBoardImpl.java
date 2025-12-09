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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BulletinBoardImpl implements BulletinBoard {
    private static final Logger logger = LoggerFactory.getLogger(BulletinBoardImpl.class);

    private static final double LOAD_FACTOR_THRESHOLD = 0.7;
    // i used this for testing the vertical scaling behavior
    //private static final double LOAD_FACTOR_THRESHOLD = 0.0000000000000000000000000000000000000000000000001;

    private final transient ServerDatabaseManager dbManager;

    private volatile BoardGeneration activeBoard;
    private final CopyOnWriteArrayList<BoardGeneration> drainingBoards = new CopyOnWriteArrayList<>();


    public BulletinBoardImpl(ServerDatabaseManager dbManager) {
        this.dbManager = dbManager;

        List<Integer> capacities = dbManager.getAllBoardCapacities();

        if (capacities.isEmpty()) {
            // No data found: Initialize normally with default size.
            int initialSize = 1024;
            this.activeBoard = new BoardGeneration(initialSize);
            logger.info("No data in DB. Initialized new board size: {}", initialSize);
        } else {
            // 1. Find the active capacity (the max)
            int activeCapacity = capacities.stream().max(Integer::compare).get();

            // 2. Create a temporary map to hold all boards during loading
            Map<Integer, BoardGeneration> boardsByCapacity = new HashMap<>();

            // 3. Load all messages
            List<ServerDatabaseManager.PersistedMessage> allMessages = dbManager.loadAllMessagesWithCapacity();

            for (ServerDatabaseManager.PersistedMessage msg : allMessages) {
                int boardCapacity = msg.boardCapacity();

                // 4. Get or create the correct board for this message
                BoardGeneration board = boardsByCapacity.computeIfAbsent(boardCapacity, capacity -> {
                    logger.info("Reconstituting board for capacity: {}", capacity);
                    return new BoardGeneration(capacity);
                });

                // 5. Load the message into its board
                board.loadMessage(msg);
            }

            // 6. Assign active and draining boards from the map
            this.activeBoard = boardsByCapacity.remove(activeCapacity); // Get and remove the active one

            // All remaining boards in the map are draining boards
            this.drainingBoards.addAll(boardsByCapacity.values());

            // Logging
            String drainingSizes = drainingBoards.stream()
                    .map(b -> String.valueOf(b.capacity))
                    .collect(Collectors.joining(", "));
            logger.info("Server loaded. Active size: {}. Draining sizes: [{}]",
                    this.activeBoard.capacity, drainingSizes.isEmpty() ? "None" : drainingSizes);
        }
    }

    @Override
    public boolean add(long idx, byte[] value, String tag) throws RemoteException {
        // Optimistic check to avoid synchronization on every add
        if (activeBoard.isOverloaded()) {
            checkAndResize();
        }
        return activeBoard.add(idx, value, tag, dbManager);
    }

    @Override
    public Pair get(long idx, String preimage) throws RemoteException {
        String tag = Encryption.preimageToTag(preimage);

        // 1. Check the DRAINING boards first
        for (BoardGeneration oldBoard : drainingBoards) {
            Pair result = oldBoard.get(idx, tag, dbManager);
            if (result != null) {
                logger.info("GET: Found in draining board size {}. Count remaining: {}", oldBoard.capacity, oldBoard.getTotalCount());

                // cleanup check
                if (oldBoard.getTotalCount() == 0) {
                    logger.info("DRAINING COMPLETE: Old board size {} is empty. Removing it.", oldBoard.capacity);
                    drainingBoards.remove(oldBoard); // Safe with CopyOnWriteArrayList
                }
                return result;
            }
        }

        // 2. If not in draining, check ACTIVE board
        return activeBoard.get(idx, tag, dbManager);
    }

    private synchronized void checkAndResize() {
        if (activeBoard.isOverloaded()) {
            logger.info("RESIZING: Board full ({} items). expanding...", activeBoard.getTotalCount());

            // The current active board becomes a new draining board.
            this.drainingBoards.add(this.activeBoard);

            // Create the new active board.
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
            // The message is placed into the bucket calculated by its original cell_index,
            // which MUST match the capacity of this BoardGeneration object.
            int index = msg.cellIndex();

            // Safety check
            if (index >= 0 && index < this.capacity) {
                this.buckets.get(index).put(msg.messageTag(), msg.messageValue());
                this.totalItems.incrementAndGet();
            } else {
                logger.error("LOAD ERROR: Message tag {} stored with index {} does not fit in board size {}. Data integrity compromised.", msg.messageTag(), index, this.capacity);
            }
        }

        public boolean add(long idx, byte[] value, String tag, ServerDatabaseManager db) {
            int index = computeIndex(idx);
            Map<String, byte[]> cell = buckets.get(index);

            synchronized (cell) {
                if (cell.containsKey(tag)) return false; // Collision within this board

                try {

                    db.saveMessage(index, this.capacity, tag, value);
                    cell.put(tag, value);
                    totalItems.incrementAndGet();
                    logger.info("DB Save successful for tag {}", tag);
                    return true;
                } catch (Exception e) {
                    logger.error("DB Save failed", e);
                    return false;
                }
            }
        }

        public Pair get(long idx, String tag, ServerDatabaseManager db) {
            int index = computeIndex(idx);
            logger.info("GET: Looking in board size {} at index {} for tag {}", this.capacity, index, tag);
            Map<String, byte[]> cell = buckets.get(index);
            byte[] value;

            synchronized (cell) {
                value = cell.remove(tag);
            }

            if (value != null) {
                try {
                    db.deleteMessage(tag);
                    totalItems.decrementAndGet();
                    return new Pair(value, tag);
                } catch (Exception e) {
                    logger.error("DB Delete failed", e);
                    // Return anyway, but system is inconsistent
                }
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