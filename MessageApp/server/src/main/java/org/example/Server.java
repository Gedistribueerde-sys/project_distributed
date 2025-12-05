package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;

public class Server {
    static void main(String[] args) throws Exception {
        Logger log = LoggerFactory.getLogger(Server.class);

        // Database setup
        ServerDatabaseManager dbManager = new ServerDatabaseManager("server_data.db");
        dbManager.initializeDatabase();

        BulletinBoardImpl bulletinBoard = new BulletinBoardImpl(dbManager);
        BulletinBoard stub = (BulletinBoard) UnicastRemoteObject.exportObject(bulletinBoard, 0);
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 1099;
        Registry registry = LocateRegistry.createRegistry(port);
        registry.rebind("BulletinBoard", stub);

        log.info("Server running on port : {}", port);
        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                UnicastRemoteObject.unexportObject(bulletinBoard, true);
                log.info("Server shut down gracefully.");
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            latch.countDown();
        }));

        log.info("Server running. Press CTRL+C to stop.");
        latch.await();
    }

}