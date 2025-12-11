package org.example;

import javafx.application.Application;
import org.example.GUI.GUI;
import org.example.controller.ChatCore;

public class Client {
    public static ChatCore chatCore;

    static void main(String[] args) {

        chatCore = new ChatCore();

        Application.launch(GUI.class, args);

    }

}