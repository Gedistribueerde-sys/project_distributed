package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public class BulletinBoardImpl implements BulletinBoard {
    private static final Logger logger = LoggerFactory.getLogger(BulletinBoardImpl.class);
    private final int BOARD_SIZE = 1024;
    private final ServerDatabaseManager dbManager;

    // A list of maps, where the list index corresponds to the cell index.
    private final List<Map<String, byte[]>> board;

    public BulletinBoardImpl(ServerDatabaseManager dbManager) {
        this.dbManager = dbManager;
        // Load the state from the database on startup
        this.board = dbManager.loadAllMessages(BOARD_SIZE);
    }

    @Override
    public boolean add(long idx, byte[] value, String tag) throws RemoteException {
        int index = computeIndex(idx);
        // get the cell where we want to write

        logger.info("ADD at index {}: tag={}, valueSize={} bytes", index, tag, value.length);
        Map<String, byte[]> cell = board.get(index);
        // Check for duplicate tag
        synchronized (cell) {
            if (cell.containsKey(tag)) {
                logger.warn("ADD FAILED: Duplicate tag {} in cell at index {}", tag, index);
                return false; // Duplicate tag, reject the add
            }
        }
        try {
            // 1. Persist to database first (Write-Through)
            dbManager.saveMessage(index, tag, value);

            // 2. Then update in-memory cache
            synchronized (cell) {
                cell.put(tag, value);
                logger.info("ADD SUCCESS: Cell at index {} now has {} entries", index, cell.size());
            }
            return true;
        } catch (Exception e) {
            logger.error("ADD FAILED: Could not persist message with tag {}", tag, e);
            // We return false to indicate the add operation failed.
            // The client is expected to retry.
            return false;
        }
    }

    @Override
    public Pair get(long idx, String preimage) throws RemoteException {
        int index = computeIndex(idx);
        logger.info("GET at index {}: preimage={}", index, preimage);

        Map<String, byte[]> cell = board.get(index);

        // Hash the preimage to get the tag
        String tag = Encryption.preimageToTag(preimage);
        logger.info("GET: computed tag from preimage: {}", tag);

        byte[] value;
        synchronized (cell) {
            logger.info("GET: Cell at index {} has {} entries. Keys: {}", index, cell.size(), cell.keySet());
            value = cell.remove(tag);
        }

        if (value != null) {
            logger.info("GET SUCCESS: Found and removed message with tag {} from cache", tag);
            try {
                // Also remove from the database
                dbManager.deleteMessage(tag);
                logger.info("GET SUCCESS: Removed message with tag {} from database", tag);
            } catch (Exception e) {
                // Log an error, but don't fail the operation from the client's perspective
                logger.error("GET FAILED: Could not remove message with tag {} from database. In-memory cache and DB are now inconsistent.", tag, e);
            }
            return new Pair(value, tag);
        }

        logger.info("GET FAILED: No message found with tag {}", tag);
        return null;
    }

    private int computeIndex(long idx) {
        return (int) ((idx % BOARD_SIZE) + BOARD_SIZE) % BOARD_SIZE;
    }
}