package org.example;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Server {
    public static void main(String[] args) throws RemoteException {
        BulletinBoard bulletinBoard = new BulletinBoardImpl();

        // for the stub, port = 0 means any available port
        BulletinBoard stub = (BulletinBoard) UnicastRemoteObject.exportObject(bulletinBoard, 0);
        Registry registry = LocateRegistry.createRegistry(1099);
        registry.rebind("BulletinBoard", stub);
    }
}