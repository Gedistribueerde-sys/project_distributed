package org.example;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;

public class Server {
public static void main(String[] args) throws Exception {
    BulletinBoardImpl bulletinBoard = new BulletinBoardImpl();
    BulletinBoard stub =
            (BulletinBoard) UnicastRemoteObject.exportObject(bulletinBoard, 0);

    Registry registry = LocateRegistry.createRegistry(1099);
    registry.rebind("BulletinBoard", stub);

    CountDownLatch latch = new CountDownLatch(1);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
            UnicastRemoteObject.unexportObject(bulletinBoard, true);
            System.out.println("Server shut down gracefully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        latch.countDown();
    }));

    System.out.println("Server running. Press CTRL+C to stop.");
    latch.await();
}

}