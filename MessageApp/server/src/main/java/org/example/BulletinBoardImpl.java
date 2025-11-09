package org.example;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

public class BulletinBoardImpl implements BulletinBoard {
    private final int N = 1024; // board size

    ArrayList<ArrayList<Pair>> board = new ArrayList<>();





    @Override
    public String sendMessage(String message) {

        // we make a arraylist containing an arraylist contianing pairs
        // Implementation here
        System.out.println("Message sent: " + message);
        return message;
    }

    @Override
    public void add(int i, String v, String t) throws RemoteException {
        if (board.get(i) == null) board.add(i, new ArrayList<>());
        board.get(i).add(new Pair(v, t));
    }

    /**
     *
     get(i, b): Let t = B(b).
     met B de hash van
     If 〈v, t〉 ∈ B[i] for some value v, return
     v and remove 〈v, t〉 from B[i]. Otherwise return ⊥, and
     leave B[i] unchanged.
     */

    @Override
    public Pair get(int i, String b) throws RemoteException {
        if (board.get(i) == null) return null;
        List<Pair> lijst = board.get(i);
        String t = Encryption.B(b);
        if (lijst != null) {
            for (Pair p : lijst) {
                if (p.getT().equals(t)) {
                    lijst.remove(p);
                    return p;
                }
            }
        }
        return null;
    }
}