package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {
    static void main(String[] args) throws Exception {
        Logger log = LoggerFactory.getLogger(Server.class);

        // Determine port, then set up database with a port-specific name
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 1099;
        String dbPath = "server_" + port + ".db";

        ServerDatabaseManager dbManager = new ServerDatabaseManager(dbPath);
        dbManager.initializeDatabase();

        BulletinBoardImpl bulletinBoard = new BulletinBoardImpl(dbManager);
        BulletinBoard stub = (BulletinBoard) UnicastRemoteObject.exportObject(bulletinBoard, 0);
        // Exporteer het bulletinBoard-object zodat het op afstand (via RMI) kan worden aangeroepen
        // Dit maakt een stub aan die clientaanroepen doorstuurt naar het echte object

        Registry registry = LocateRegistry.createRegistry(port);
        // Start een RMI-registry op de opgegeven poort zodat clients het remote object kunnen vinden

        registry.rebind("BulletinBoard", stub);
        // Registreer (of vervang) het remote object onder de naam "BulletinBoard" in de registry
        // Clients kunnen het object opzoeken met deze naam

        log.info("Server running on port: {}", port);

        // --- Orphaned Message Cleanup Task ---
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        // Maak een scheduler met één enkele thread voor periodieke taken
            Thread t = new Thread(r, "Orphaned-Message-Cleaner-Thread");
            // Geef de thread een herkenbare naam
            t.setDaemon(true);
            // Maak er een daemon-thread van zodat ze de JVM niet blokkeert bij afsluiten
            return t;
        });

        scheduler.scheduleAtFixedRate(
                bulletinBoard::cleanUpOrphanedMessages,
                // Methode-referentie: deze cleanup-methode wordt periodiek uitgevoerd
                1,
                // Start de eerste uitvoering na 1 minuut
                1,
                // Herhaal de taak elke 1 minuut
                TimeUnit.MINUTES
        );

        log.info("Orphaned message cleanup task scheduled to run every minute.");
        // Log dat de opruimtaak correct werd ingepland
        CountDownLatch latch = new CountDownLatch(1);
        // Synchronisatie-object dat de hoofdthread laat wachten tot de server wordt afgesloten
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        // Registreer een shutdown hook die wordt uitgevoerd wanneer de JVM afsluit (bv. CTRL+C)
            try {
                scheduler.shutdown();
                // Stop de scheduler netjes zodat lopende taken worden afgerond

                UnicastRemoteObject.unexportObject(bulletinBoard, true);
                // Maak het RMI-object onbeschikbaar voor nieuwe remote aanroepen

                log.info("Server shut down gracefully.");
                // Log dat de server correct werd afgesloten
            } catch (Exception e) {
                log.error(e.getMessage());
                // Log eventuele fouten tijdens het afsluiten
            }
            latch.countDown();
            // Geef de latch vrij zodat de hoofdthread kan stoppen
        }));

        log.info("Server running. Press CTRL+C to stop.");
        // Log dat de server actief is en wacht op afsluiten

        latch.await();
        // Laat de hoofdthread blokkeren tot de shutdown hook de latch vrijgeeft
    }
}