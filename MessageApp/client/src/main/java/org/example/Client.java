package org.example;

import javafx.application.Application;
import org.example.GUI.GUI;
import org.example.controller.ChatCore;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class Client {
    public static ChatCore chatCore;

    static void main(String[] args) throws RemoteException, NotBoundException {
        chatCore = new ChatCore();
        Application.launch(GUI.class, args);

    }

}