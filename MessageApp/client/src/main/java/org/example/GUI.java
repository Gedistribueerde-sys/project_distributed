package org.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class GUI extends Application {

    private Controller controller;

    @Override
    public void start(Stage stage) {
        this.controller = Main.controller; // abstractielaag gebruiken

        stage.setTitle("Message App");
        showLoginScene(stage);
        stage.show();
    }

    private void showLoginScene(Stage stage) {
        TextField usernameField = new TextField();
        usernameField.setPromptText("Gebruikersnaam");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Wachtwoord");

        Label statusLabel = new Label();

        Button loginButton = new Button("Login");
        Button registerButton = new Button("Register");

        HBox buttons = new HBox(10, loginButton, registerButton);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(10,
                new Label("Login of registreer om te starten"),
                usernameField,
                passwordField,
                buttons,
                statusLabel
        );
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, 400, 250);
        stage.setScene(scene);

        loginButton.setOnAction(e -> {
            String user = usernameField.getText();
            String pass = passwordField.getText();
            if (controller.login(user, pass)) {
                statusLabel.setText("Login ok!");
                showChatScene(stage);
            } else {
                statusLabel.setText("Login mislukt");
            }
        });

        registerButton.setOnAction(e -> {
            String user = usernameField.getText();
            String pass = passwordField.getText();
            if (controller.register(user, pass)) {
                statusLabel.setText("Registratie geslaagd, log nu in");
            } else {
                statusLabel.setText("Registratie mislukt (naam bestaat al of ongeldig)");
            }
        });
    }

    private void showChatScene(Stage stage) {
        String username = controller.getCurrentUser();
        if (username == null) {
            showLoginScene(stage);
            return;
        }

        ListView<String> messagesView = new ListView<>();
        messagesView.getItems().addAll(controller.getMessages());

        TextField messageField = new TextField();
        messageField.setPromptText("Typ je bericht...");

        Button sendButton = new Button("Send");
        Button logoutButton = new Button("Logout");

        Label infoLabel = new Label("Ingelogd als: " + username);

        HBox inputBox = new HBox(10, messageField, sendButton);
        HBox.setHgrow(messageField, Priority.ALWAYS);

        HBox topBar = new HBox(10, infoLabel, logoutButton);
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, topBar, messagesView, inputBox);
        root.setPadding(new Insets(10));

        Scene chatScene = new Scene(root, 600, 400);
        stage.setScene(chatScene);

        sendButton.setOnAction(e -> {
            String text = messageField.getText();
            if (text == null || text.isBlank()) return;

            controller.sendMessage(text);
            messageField.clear();

            messagesView.getItems().setAll(controller.getMessages());
            messagesView.scrollTo(messagesView.getItems().size() - 1);
        });

        logoutButton.setOnAction(e -> {
            controller.logout();
            showLoginScene(stage);
        });
    }
}
