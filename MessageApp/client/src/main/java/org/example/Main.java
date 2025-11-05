package org.example;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Main {
    public static void main(String[] args) throws RemoteException, NotBoundException {
        Registry locateRegistry = LocateRegistry.getRegistry();
        BulletinBoard bulletinBoard = (BulletinBoard) locateRegistry.lookup("BulletinBoard");
        String response = bulletinBoard.sendMessage("Hello from the client!");
        System.out.println("Response from server: " + response);

    }
}