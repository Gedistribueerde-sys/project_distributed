package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;

public class GUI extends Application {
    private static final Logger log = LoggerFactory.getLogger(GUI.class);

    private Controller controller;
    private final ListView<String> chatList = new ListView<>();

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
        usernameField.setPromptText("Username");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Label statusLabel = new Label();

        Button loginButton = new Button("Login");
        Button registerButton = new Button("Register");

        HBox buttons = new HBox(10, loginButton, registerButton);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(10,
                new Label("Login or register to start"),
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
                statusLabel.setText("Login successful!");
                showChatScene(stage);
            } else {
                statusLabel.setText("Login failed");
            }
        });

        registerButton.setOnAction(e -> {
            String user = usernameField.getText();
            String pass = passwordField.getText();
            if (controller.register(user, pass)) {
                statusLabel.setText("Registration successful, please log in now");
            } else {
                statusLabel.setText("Registration failed (name already exists or is invalid)");
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

        // ----- RIGHT: chat window -----
        ListView<String> messagesView = new ListView<>();
        messagesView.getItems().add("Select a chat to get started...");
        messagesView.setStyle("-fx-background-color: white;");

        // Debug window with key/idx/tag info
        TextArea stateView = new TextArea();
        stateView.setEditable(false);
        stateView.setPrefRowCount(5);
        stateView.setWrapText(true);
        stateView.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");

        // top bar
        Label userLabel = new Label("Logged in as: " + controller.getCurrentUser());
        userLabel.setStyle("-fx-font-weight: bold;");
        Button logoutButton = new Button("Logout");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(10, userLabel, spacer, logoutButton);

        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-padding: 5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        // bottom input
        TextField messageField = new TextField();
        messageField.setPromptText("Type a message...");
        messageField.setStyle("-fx-background-radius: 20; -fx-padding: 8;");
        Button sendButton = new Button("Send");
        Button fetchButton = new Button("Fetch");



        HBox inputBar = new HBox(10, messageField, sendButton, fetchButton);
        inputBar.setPadding(new Insets(5, 0, 0, 0));
        HBox.setHgrow(messageField, Priority.ALWAYS);

        VBox rightBox = new VBox(10, topBar, stateView, messagesView, inputBar);
        rightBox.setPadding(new Insets(10));
        VBox.setVgrow(messagesView, Priority.ALWAYS);

        // ----- SPLITPANE (left + right) -----
        SplitPane splitPane = new SplitPane(leftBox, rightBox);
        splitPane.setDividerPositions(0.30);

        Scene scene = new Scene(splitPane, 900, 600);
        stage.setScene(scene);
        stage.show();

        // ----- INTERACTION LOGIC -----
        logoutButton.setOnAction(e -> {
            controller.logout();
            showLoginScene(stage);
        });
        // when a chat is selected
        // 0 = "➕ New Chat (BUMP)"
        // 1..n = existing chats
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            messagesView.getItems().clear();

            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex < 0) return;
            // handle bump option here
            if (newVal.startsWith("➕")) {
                // BUMP entry clicked
                stateView.clear();
                messagesView.getItems().add("Use the dialog to start a chat...");

                // Postpone anything "large" until after this event
                Platform.runLater(() -> {
                    showNewChatDialog(stage);   // open dialog
                    updateChatList();          // reload list
                    chatList.getSelectionModel().clearSelection(); // clear selection (optional)
                });

            } else {
                // Existing chat
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
                new Alert(Alert.AlertType.WARNING, "Please select an existing chat first.").showAndWait();
                return;
            }

            controller.sendMessage(selectedIndex, text);

            // Show locally in the messagesView
            messagesView.getItems().add("Me: " + text);
            messageField.clear();

            // Update debug panel (chain has advanced)
            String debugText = controller.getDebugStateForIndex(selectedIndex);
            stateView.setText(debugText);
        });
        fetchButton.setOnAction(e -> {
            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex <= 0) {
                new Alert(Alert.AlertType.WARNING, "Please select an existing chat first.").showAndWait();
                return;
            }

            try {
                log.info("GUI: fetchMessages for index {}", selectedIndex);

                var newMessages = controller.fetchMessages(selectedIndex);

                if (newMessages.isEmpty()) {
                    messagesView.getItems().add("No new messages.");
                } else {
                    for (String m : newMessages) {
                        messagesView.getItems().add(controller.getRecipientName(selectedIndex) + " : " + m);
                    }
                }

                // Update debug after recv-chain update
                String debugText = controller.getDebugStateForIndex(selectedIndex);
                stateView.setText(debugText);

            } catch (RemoteException ex) {
                log.error("Error fetching messages", ex);
                new Alert(Alert.AlertType.ERROR, "Error fetching messages.").showAndWait();
            }
        });

    }
    // asks the controller what chats exist and fills the ListView
    private void updateChatList() {
        if (chatList != null) {
            chatList.getItems().setAll(controller.getChatNames());
        }
    }
    // this is for the bump
    private void showNewChatDialog(Stage parentStage) {
        // create dialog
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("New Chat (Bump Simulation)");
        dialog.setHeaderText(
                "1. Copy your code and send it to the other user.\n" +
                        "2. Paste the code you receive from the other user below to set up the chat."
        );

        ButtonType acceptButtonType = new ButtonType("Accept Code", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(acceptButtonType, cancelButtonType);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        // --- own text field (read-only + copy button) ---
        TextArea myBumpArea = new TextArea();
        myBumpArea.setEditable(false);
        myBumpArea.setWrapText(true);
        myBumpArea.setPrefRowCount(3);

        Button copyToClipboardButton = new Button("Copy my code");

        // --- text field for the OTHER (to paste into) ---
        TextArea bumpInputArea = new TextArea();
        bumpInputArea.setPromptText("Paste the received code here (name|key|idx|tag)");
        bumpInputArea.setWrapText(true);
        bumpInputArea.setPrefRowCount(3);

        Label statusLabel = new Label("Your code is being generated...");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
        //Everything neatly arranged in a VBox in the dialog.
        content.getChildren().addAll(
                new Label("YOUR BUMP-CODE:"),
                myBumpArea,
                copyToClipboardButton,
                new Separator(),
                new Label("OTHER'S CODE:"),
                bumpInputArea,
                new Separator(),
                statusLabel
        );

        dialog.getDialogPane().setContent(content);

        // --- generate own bump-string ---
        // in the controller; this will then be left in the text field
        try {
            String myBump = controller.generateOwnBumpString();
            myBumpArea.setText(myBump);
            statusLabel.setText("Copy your code and send it to the other user.");
        } catch (Exception ex) {
            statusLabel.setText("Error generating your code: " + ex.getMessage());
            log.error("Error generating your code", ex);
        }

        // Copy button
        copyToClipboardButton.setOnAction(e -> {
            final javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            final javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
            clipboardContent.putString(myBumpArea.getText());
            clipboard.setContent(clipboardContent);
            statusLabel.setText("Code copied to clipboard!");
        });

        // Accept button
        final Button acceptBtn = (Button) dialog.getDialogPane().lookupButton(acceptButtonType);
        acceptBtn.disableProperty().bind(bumpInputArea.textProperty().isEmpty());

        acceptBtn.setOnAction(event -> {
            event.consume();

            String myCode = myBumpArea.getText().trim();
            String otherCode = bumpInputArea.getText().trim();

            if (otherCode.isEmpty()) {
                statusLabel.setText("Please paste the other user's code first.");
                return;
            }

            if (controller.acceptNewChat(myCode, otherCode)) {
                statusLabel.setText("Chat created successfully!");
                dialog.close();
            } else {
                statusLabel.setText("Error accepting: check the codes.");
            }
        });

        dialog.setResultConverter(dbtn -> null);
        dialog.showAndWait();
    }


}
