package org.example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BulletinBoard extends Remote {
    void add(long idx, byte[] value, String tag) throws RemoteException;
    Pair get(long idx, String preimage) throws RemoteException;
}
