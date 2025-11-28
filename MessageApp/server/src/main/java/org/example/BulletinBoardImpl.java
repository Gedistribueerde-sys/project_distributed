package org.example;

import java.rmi.RemoteException;
import java.util.*;

public class BulletinBoardImpl implements BulletinBoard {
    private final int BOARD_SIZE = 1024;

    private final ArrayList<Set<Pair>> board;
    BulletinBoardImpl() {
        // Initialize the board with empty sets
        board = new ArrayList<>(BOARD_SIZE);
        for (int i = 0; i < BOARD_SIZE; i++) {
            board.add(new HashSet<>());
        }
    }


    @Override
    public void add(int idx, String value, String tag) throws RemoteException {
        // Bring idx within the range [0, N-1]
        idx = ((idx % BOARD_SIZE) + BOARD_SIZE) % BOARD_SIZE; // This calculation ensures idx is positive
        System.out.println("Adding to board at index " + idx + ": <" + value + ", " + tag + ">");
        Set<Pair> cell = board.get(idx);
        if (cell == null) {
            cell = new HashSet<>();
            board.set(idx, cell);
        }

        // Add the pair <v, t> to the Set
        cell.add(new Pair(value, tag));
    }


    @Override
    public Pair get(int idx, String preimage) throws RemoteException {

        // Bring idx within the range [0, N-1]
        idx = ((idx % BOARD_SIZE) + BOARD_SIZE) % BOARD_SIZE; // This calculation ensures idx is positive
        System.out.println("Getting from board at index " + idx + " with preimage = " + preimage);

        Set<Pair> cell = board.get(idx);
        if (cell == null || cell.isEmpty()) {
            return null;
        }

        String t = Encryption.preimageToTag(preimage);

        // Use an iterator so we can safely remove during iteration
        Iterator<Pair> it = cell.iterator();
        while (it.hasNext()) {
            Pair p = it.next();
            if (p.tag().equals(t)) {
                it.remove();
                return p;
            }
        }

        // Nothing found for the idx and preimage/tag
        return null;
    }

}