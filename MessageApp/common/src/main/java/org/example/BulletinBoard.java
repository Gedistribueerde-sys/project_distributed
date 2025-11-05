package org.example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BulletinBoard extends Remote {
    String sendMessage(String message) throws RemoteException;
}
