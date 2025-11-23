package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.rmi.RemoteException;

public class GUI extends Application {

    private Controller controller;
    private ListView<String> chatList = new ListView<>();
    private Label userLabel;
    // eerste methode die automatisch opgeroepen wordt
    @Override
    public void start(Stage stage) {
        this.controller = Client.controller; // abstractielaag gebruiken

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

        updateChatList(); // Vul de lijst met de controller data

        Label leftTitle = new Label("Chats");
        leftTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        VBox leftBox = new VBox(10, leftTitle, chatList);
        leftBox.setPadding(new Insets(10));
        leftBox.setPrefWidth(250);
        leftBox.setStyle("-fx-background-color: #f0f0f0;");
        VBox.setVgrow(chatList, Priority.ALWAYS);

        // ----- RECHTS: chatvenster -----
        ListView<String> messagesView = new ListView<>();
        messagesView.getItems().add("Selecteer een chat om te beginnen...");
        messagesView.setStyle("-fx-background-color: white;");

        // Debug-venster met key/idx/tag info
        TextArea stateView = new TextArea();
        stateView.setEditable(false);
        stateView.setPrefRowCount(5);
        stateView.setWrapText(true);
        stateView.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");

        // top bar
        userLabel = new Label("Ingelogd als: " + controller.getCurrentUser());
        userLabel.setStyle("-fx-font-weight: bold;");
        Button logoutButton = new Button("Logout");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(10, userLabel, spacer, logoutButton);

        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-padding: 5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        // bottom input
        TextField messageField = new TextField();
        messageField.setPromptText("Typ een bericht...");
        messageField.setStyle("-fx-background-radius: 20; -fx-padding: 8;");
        Button sendButton = new Button("Send");
        Button fetchButton = new Button("Fetch");



        HBox inputBar = new HBox(10, messageField, sendButton, fetchButton);
        inputBar.setPadding(new Insets(5, 0, 0, 0));
        HBox.setHgrow(messageField, Priority.ALWAYS);

        VBox rightBox = new VBox(10, topBar, stateView, messagesView, inputBar);
        rightBox.setPadding(new Insets(10));
        VBox.setVgrow(messagesView, Priority.ALWAYS);

        // ----- SPLITPANE (links + rechts) -----
        SplitPane splitPane = new SplitPane(leftBox, rightBox);
        splitPane.setDividerPositions(0.30);

        Scene scene = new Scene(splitPane, 900, 600);
        stage.setScene(scene);
        stage.show();

        // ----- INTERACTIE LOGICA -----
        logoutButton.setOnAction(e -> {
            controller.logout();
            showLoginScene(stage);
        });
        // wanneer een chat geselecteerd wordt
        // 0 = "➕ Nieuwe chat (BUMP)"
        // 1..n = bestaande chats
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            messagesView.getItems().clear();

            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex < 0) return;
            // hier bump optie afhandelen
            if (newVal.startsWith("➕")) {
                // BUMP-entry aangeklikt
                stateView.clear();
                messagesView.getItems().add("Gebruik de dialoog om een chat te starten...");

                // Alles wat “groot” is uitstellen tot ná dit event
                Platform.runLater(() -> {
                    showNewChatDialog(stage);   // dialog openen
                    updateChatList();          // lijst herladen
                    chatList.getSelectionModel().clearSelection(); // selectie wissen (optioneel)
                });

            } else {
                // Bestaande chat
                String debugText = controller.getDebugStateForIndex(selectedIndex);
                stateView.setText(debugText);

                messagesView.getItems().add(newVal);

            }
        });
        sendButton.setOnAction(e -> {
            String text = messageField.getText().trim();
            if (text.isEmpty()) return;

            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex <= 0) {
                new Alert(Alert.AlertType.WARNING, "Selecteer eerst een bestaande chat.").showAndWait();
                return;
            }

            controller.sendMessage(selectedIndex, text);

            // Toon lokaal in de messagesView
            messagesView.getItems().add("Ik: " + text);
            messageField.clear();

            // Debug-paneel updaten (ketting is vooruit gegaan)
            String debugText = controller.getDebugStateForIndex(selectedIndex);
            stateView.setText(debugText);
        });
        fetchButton.setOnAction(e -> {
            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex <= 0) {
                new Alert(Alert.AlertType.WARNING, "Selecteer eerst een bestaande chat.").showAndWait();
                return;
            }

            try {
                System.out.println("GUI: fetchMessages voor index " + selectedIndex);
                var nieuw = controller.fetchMessages(selectedIndex);

                if (nieuw.isEmpty()) {
                    messagesView.getItems().add("Geen nieuwe berichten.");
                } else {
                    for (String m : nieuw) {
                        messagesView.getItems().add(controller.getRecipientName(selectedIndex) + " : " + m);
                    }
                }

                // Debug bijwerken na recv-ketting update
                String debugText = controller.getDebugStateForIndex(selectedIndex);
                stateView.setText(debugText);

            } catch (RemoteException ex) {
                ex.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Fout bij ophalen van berichten.").showAndWait();
            }
        });

    }
    // vraagt aan de controller welke chats er zijn en vult de ListView
    private void updateChatList() {
        if (chatList != null) {
            chatList.getItems().setAll(controller.getChatNames());
        }
    }
    // dit is voor de bump
    private void showNewChatDialog(Stage parentStage) {
        // dialoog maken
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Nieuwe Chat (Bump Simulatie)");
        dialog.setHeaderText(
                "1. Kopieer jouw code en stuur die naar de andere gebruiker.\n" +
                        "2. Plak hieronder de code die jij van de ander krijgt om de chat op te zetten."
        );

        ButtonType acceptButtonType = new ButtonType("Accepteer Code", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Annuleer", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(acceptButtonType, cancelButtonType);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        // --- eigen tekstvak (read-only + kopieerknop) ---
        TextArea myBumpArea = new TextArea();
        myBumpArea.setEditable(false);
        myBumpArea.setWrapText(true);
        myBumpArea.setPrefRowCount(3);

        Button copyToClipboardButton = new Button("Kopieer mijn code");

        // --- tekstvak voor de ANDER (in te plakken) ---
        TextArea bumpInputArea = new TextArea();
        bumpInputArea.setPromptText("Plak hier de ontvangen code (naam|key|idx|tag)");
        bumpInputArea.setWrapText(true);
        bumpInputArea.setPrefRowCount(3);

        Label statusLabel = new Label("Jouw code wordt gegenereerd...");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
        //Alles netjes onder elkaar in een VBox in de dialoog.
        content.getChildren().addAll(
                new Label("JOUW BUMP-CODE:"),
                myBumpArea,
                copyToClipboardButton,
                new Separator(),
                new Label("CODE VAN DE ANDER:"),
                bumpInputArea,
                new Separator(),
                statusLabel
        );

        dialog.getDialogPane().setContent(content);

        // --- de eigen bump-string genereren ---
        // in de controller ; dit wordt dan in eht tekstveld gelaten
        try {
            String myBump = controller.generateOwnBumpString();
            myBumpArea.setText(myBump);
            statusLabel.setText("Kopieer jouw code en stuur die naar de andere gebruiker.");
        } catch (Exception ex) {
            statusLabel.setText("Fout bij genereren van jouw code: " + ex.getMessage());
            ex.printStackTrace();
        }

        // Kopieer-knop
        copyToClipboardButton.setOnAction(e -> {
            final javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            final javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
            clipboardContent.putString(myBumpArea.getText());
            clipboard.setContent(clipboardContent);
            statusLabel.setText("Code gekopieerd naar klembord!");
        });

        // Accept-knop
        final Button acceptBtn = (Button) dialog.getDialogPane().lookupButton(acceptButtonType);
        acceptBtn.disableProperty().bind(bumpInputArea.textProperty().isEmpty());

        acceptBtn.setOnAction(event -> {
            event.consume();

            String myCode = myBumpArea.getText().trim();
            String otherCode = bumpInputArea.getText().trim();

            if (otherCode.isEmpty()) {
                statusLabel.setText("Plak eerst de code van de andere gebruiker.");
                return;
            }

            if (controller.acceptNewChat(myCode, otherCode)) {
                statusLabel.setText("Chat succesvol aangemaakt!");
                dialog.close();
            } else {
                statusLabel.setText("Fout bij accepteren: controleer de codes.");
            }
        });

        dialog.setResultConverter(dbtn -> null);
        dialog.showAndWait();
    }


}
