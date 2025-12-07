package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BulletinBoardImpl implements BulletinBoard {
    private static final Logger logger = LoggerFactory.getLogger(BulletinBoardImpl.class);

    private static final double LOAD_FACTOR_THRESHOLD = 0.7;
    // i used this for testing the vertical scaling behavior
    //private static final double LOAD_FACTOR_THRESHOLD = 0.0000000000000000000000000000000000000000000000001;

    private final transient ServerDatabaseManager dbManager;

    private volatile BoardGeneration activeBoard;
    private volatile BoardGeneration drainingBoard; // The old board we are emptying

    public BulletinBoardImpl(ServerDatabaseManager dbManager) {
        this.dbManager = dbManager;



        // Discover all board capacities currently in the database
        List<Integer> capacities = dbManager.getAllBoardCapacities();

        if (capacities.isEmpty()) {
            // No data found: Initialize normally with default size.
            int initialSize = 1024;
            this.activeBoard = new BoardGeneration(initialSize);
            logger.info("No data in DB. Initialized new board size: {}", initialSize);
        } else {
            // Identify the largest (most recent) capacity
            int activeCapacity = capacities.stream().max(Integer::compare).get();
            this.activeBoard = new BoardGeneration(activeCapacity);
            // 3. Load ALL messages and re-index them into the correct BoardGeneration objects
            List<ServerDatabaseManager.PersistedMessage> allMessages = dbManager.loadAllMessagesWithCapacity();

            for (ServerDatabaseManager.PersistedMessage msg : allMessages) {
                // Determine which board generation this message belongs to
                if (msg.boardCapacity() == activeCapacity) {
                    // Add to the active board
                    this.activeBoard.loadMessage(msg);
                } else {
                    // This is an older, draining message
                    if (this.drainingBoard == null || this.drainingBoard.capacity != msg.boardCapacity()) {
                        this.drainingBoard = new BoardGeneration(msg.boardCapacity());
                        logger.warn("Server restarting mid-drain. Reconstituting draining board size: {}", msg.boardCapacity());
                    }
                    this.drainingBoard.loadMessage(msg);
                }
            }
            logger.info("Server loaded. Active size: {}. Draining size: {}",
                    this.activeBoard.capacity,
                    this.drainingBoard == null ? "None" : this.drainingBoard.capacity);
        }


    }

    @Override
    public boolean add(long idx, byte[] value, String tag) throws RemoteException {
        // 1. Check if we need to resize before adding
        checkAndResize();

        // 2. Always write to the ACTIVE (new) board
        return activeBoard.add(idx, value, tag, dbManager);
    }

    @Override
    public Pair get(long idx, String preimage) throws RemoteException {
        String tag = Encryption.preimageToTag(preimage);

        // 1. Check the DRAINING board first (if it exists)
        BoardGeneration oldBoard = this.drainingBoard;
        if (oldBoard != null) {
            Pair result = oldBoard.get(idx, tag, dbManager);
            if (result != null) {
                logger.info("GET: Found in draining board. Count remaining: {}", oldBoard.getTotalCount());

                // cleanup check
                if (oldBoard.getTotalCount() == 0) {
                    logger.info("DRAINING COMPLETE: Old board is empty. GC will reclaim it.");
                    this.drainingBoard = null;
                }
                return result;
            }
        }

        // 2. If not in draining, check ACTIVE board
        return activeBoard.get(idx, tag, dbManager);
    }

    private synchronized void checkAndResize() {

        if (activeBoard.isOverloaded()) {
            if (drainingBoard != null) {
                // We are already draining one board.
                // Option A: Block and force merge (slow)
                // Option B: Reject resize (dangerous)
                // Option C: Just log warning. We shouldn't resize again until the first drain is done. this is wat we do here
                logger.warn("Load high, but still draining previous board. Skipping resize.");
                return;
            }

            logger.info("RESIZING: Board full ({} items). expanding...", activeBoard.getTotalCount());


            this.drainingBoard = this.activeBoard;


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