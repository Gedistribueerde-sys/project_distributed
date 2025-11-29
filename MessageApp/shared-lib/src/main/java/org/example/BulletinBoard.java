package org.example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BulletinBoard extends Remote {
    void add(int idx, String value, String tag) throws RemoteException;
    Pair get(int idx, String preimage) throws RemoteException;
}
