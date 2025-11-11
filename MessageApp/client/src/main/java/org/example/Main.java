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
    public static final Controller controller = new Controller();

    public static void main(String[] args) throws RemoteException, NotBoundException {

        /*
        Registry locateRegistry = LocateRegistry.getRegistry();
        BulletinBoard bulletinBoard = (BulletinBoard) locateRegistry.lookup("BulletinBoard");
        String response = bulletinBoard.sendMessage("Hello from the client!");
        System.out.println("Response from server: " + response);

        */

        Application.launch(GUI.class, args);

    }

}