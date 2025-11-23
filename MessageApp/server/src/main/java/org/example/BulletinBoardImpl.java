package org.example;

import java.rmi.RemoteException;
import java.util.*;

public class BulletinBoardImpl implements BulletinBoard {
    private final int N = 1024; // board size
    private ArrayList<Set<Pair>> board;
    BulletinBoardImpl() {
        // Initialiseer het bord met lege sets
        board = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            board.add(new HashSet<>());
        }
    }


    @Override
    public void add(int i, String v, String t) throws RemoteException {
        // Breng i binnen het bereik [0, N-1]
        i = ((i % N) + N) % N;
        System.out.println("Adding to board at index " + i + ": <" + v + ", " + t + ">");
        Set<Pair> cell = board.get(i);
        if (cell == null) {
            cell = new HashSet<>();
            board.set(i, cell);
        }

        // Voeg het paar <v, t> toe aan de Set
        cell.add(new Pair(v, t));
    }

    /**
     * get(i, b): Let t = B(b).
     * If <v, t> ∈ B[i] for some value v, return v and remove <v, t> from B[i].
     * Otherwise return ⊥ (hier: null), en laat B[i] ongewijzigd.
     */
    @Override
    public Pair get(int i, String b) throws RemoteException {

        // Breng i binnen het bereik [0, N-1]
        i = ((i % N) + N) % N;
        System.out.println("Getting from board at index " + i + " with b = " + b);


        Set<Pair> cell = board.get(i);
        if (cell == null || cell.isEmpty()) {
            return null;
        }

        // Bereken t = B(b)
        String t = Encryption.B(b);

        // Gebruik een iterator zodat we veilig kunnen verwijderen tijdens iteratie
        Iterator<Pair> it = cell.iterator();
        while (it.hasNext()) {
            Pair p = it.next();
            if (p.getT().equals(t)) {
                // Verwijder dit element uit de Set
                it.remove();
                // Geef het gevonden pair terug
                return p;
            }
        }

        // Niets gevonden met deze tag
        return null;
    }

}