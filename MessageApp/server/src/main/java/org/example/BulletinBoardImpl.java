package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // Thread-safe for RMI

public class BulletinBoardImpl implements BulletinBoard {
    private static final Logger logger = LoggerFactory.getLogger(BulletinBoardImpl.class);
    private final int BOARD_SIZE = 1024;

    // Map: Key = Tag (String/Base64), Value = Encrypted Message (byte[])
    private final List<Map<String, byte[]>> board;

    public BulletinBoardImpl() {
        board = new ArrayList<>(BOARD_SIZE);
        for (int i = 0; i < BOARD_SIZE; i++) {
            board.add(new ConcurrentHashMap<>());
        }
    }

    @Override
    public boolean add(long idx, byte[] value, String tag) throws RemoteException {
        int index = computeIndex(idx);
        logger.info("ADD at index {}: tag={}, valueSize={} bytes", index, tag, value.length);

        // O(1) - Instant access
        Map<String, byte[]> cell = board.get(index);
        synchronized (cell) {
            cell.put(tag, value);
            logger.info("ADD SUCCESS: Cell at index {} now has {} entries", index, cell.size());
        }
        return true;
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
            logger.info("GET SUCCESS: Found and removed message with tag {}", tag);
            return new Pair(value, tag);
        }

        logger.info("GET FAILED: No message found with tag {}", tag);
        return null;
    }

    private int computeIndex(long idx) {
        return (int) ((idx % BOARD_SIZE) + BOARD_SIZE) % BOARD_SIZE;
    }
}