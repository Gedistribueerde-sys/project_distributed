package org.example;

import javafx.application.Application;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Client {
    public static  Controller controller;

    static void main(String[] args) throws RemoteException, NotBoundException {


        Registry locateRegistry = LocateRegistry.getRegistry();
        BulletinBoard bulletinBoard = (BulletinBoard) locateRegistry.lookup("BulletinBoard");
        System.out.println("Connected to BulletinBoard RMI server.");

        controller = new Controller(bulletinBoard);


        Application.launch(GUI.class, args);

    }

}