package org.example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BulletinBoard extends Remote {
    String sendMessage(String message) throws RemoteException;

    void add(int i, String v, String t ) throws RemoteException;
    Pair get(int i , String b) throws RemoteException;

}
