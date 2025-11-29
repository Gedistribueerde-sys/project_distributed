package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // Thread-safe for RMI

public class BulletinBoardImpl implements BulletinBoard {
    private static final Logger logger = LoggerFactory.getLogger(BulletinBoardImpl.class);
    private final int BOARD_SIZE = 1024;

    // Map: Key = Tag (String), Value = Message (String)
    private final List<Map<String, String>> board;

    public BulletinBoardImpl() {
        board = new ArrayList<>(BOARD_SIZE);
        for (int i = 0; i < BOARD_SIZE; i++) {
            //
            board.add(new ConcurrentHashMap<>());
        }
    }

    @Override
    public void add(int idx, String value, String tag) throws RemoteException {
        int index = computeIndex(idx);
        logger.info("Adding to board at index {}: <{}, {}>", index, value, tag);

        // O(1) - Instant access
        Map<String, String> cell = board.get(index);
        synchronized (cell) {
            cell.put(tag, value);
        }
    }

    @Override
    public Pair get(int idx, String preimage) throws RemoteException {
        int index = computeIndex(idx);
        logger.info("Getting from board at index {}", index);

        Map<String, String> cell = board.get(index);

        String tag = Encryption.preimageToTag(preimage);

        String value;
        synchronized (cell) {
            value = cell.remove(tag);
        }

        if (value != null) {
            return new Pair(value, tag);
        }

        return null;
    }

    private int computeIndex(int idx) {
        return ((idx % BOARD_SIZE) + BOARD_SIZE) % BOARD_SIZE;
    }
}