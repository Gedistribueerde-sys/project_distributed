package org.example;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;



import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class Main {
    public static void main(String[] args) throws RemoteException, NotBoundException {

        /*
        Registry locateRegistry = LocateRegistry.getRegistry();
        BulletinBoard bulletinBoard = (BulletinBoard) locateRegistry.lookup("BulletinBoard");
        String response = bulletinBoard.sendMessage("Hello from the client!");
        System.out.println("Response from server: " + response);

        */
        Application.launch(SimpleWindow.class, args);

    }
    public static class SimpleWindow extends Application {
        @Override
        public void start(Stage stage) {
            Label label = new Label("Hallo JavaFX vanuit main!");
            Scene scene = new Scene(label, 400, 200);

            stage.setTitle("Eenvoudig JavaFX-venster");
            stage.setScene(scene);
            stage.show();
        }
    }
}