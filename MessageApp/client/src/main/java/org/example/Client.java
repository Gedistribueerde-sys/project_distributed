package org.example;

import javafx.application.Application;
import org.example.GUI.GUI;
import org.example.controller.ChatCore;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Client {
    public static ChatCore chatCore;

    static void main(String[] args) throws RemoteException, NotBoundException {


        Registry locateRegistry = LocateRegistry.getRegistry();
        BulletinBoard bulletinBoard = (BulletinBoard) locateRegistry.lookup("BulletinBoard");
        System.out.println("Connected to BulletinBoard RMI server.");

        chatCore = new ChatCore(bulletinBoard);


        Application.launch(GUI.class, args);

    }

}