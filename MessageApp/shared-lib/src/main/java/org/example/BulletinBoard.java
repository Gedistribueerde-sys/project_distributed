package org.example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BulletinBoard extends Remote {
    boolean add(long idx, byte[] value, String tag, long nonce) throws RemoteException;

    Pair get(long idx, String preimage) throws RemoteException;
    boolean confirm(long idx, String tag) throws RemoteException;
}
