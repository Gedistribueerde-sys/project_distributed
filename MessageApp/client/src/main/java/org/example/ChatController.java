package org.example;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.List;

public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @FXML
    private ListView<String> chatList;
    @FXML
    private Label userLabel;
    @FXML
    private TextArea stateView;
    @FXML
    private ListView<Message> messagesView;
    @FXML
    private TextField messageField;

    private Controller controller;
    private Stage stage;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        // This is called after the FXML file has been loaded
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex < 0) return;

            messagesView.getItems().clear();

            if (newVal.startsWith("➕")) {
                stateView.clear();
                messagesView.getItems().add(new Message("system", "Use the dialog to start a chat..."));
                Platform.runLater(() -> {
                    showNewChatDialog();
                    updateChatList();
                    chatList.getSelectionModel().clearSelection();
                });
            } else {
                String debugText = controller.getDebugStateForIndex(selectedIndex);
                stateView.setText(debugText);
                // show a header message with the chat name (keeps previous behavior but uses Message model)
                messagesView.getItems().add(new Message(controller.getRecipientName(selectedIndex), newVal));
            }
        });
    }

    public void setup() {
        userLabel.setText("Logged in as: " + controller.getCurrentUser());
        // set the custom cell factory now that controller/current user is available
        messagesView.setCellFactory(lv -> new MessageCell(controller.getCurrentUser()));
        updateChatList();
    }

    @FXML
    private void handleLogout() {
        controller.logout();
        // The GUI class will handle the scene change
    }

    @FXML
    private void handleSend() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
        if (selectedIndex <= 0) {
            new Alert(Alert.AlertType.WARNING, "Please select an existing chat first.").showAndWait();
            return;
        }

        controller.sendMessage(selectedIndex, text);
        // add as a Message with sender = current user so the cell renders as outgoing
        messagesView.getItems().add(new Message(controller.getCurrentUser(), text));
        messageField.clear();
        String debugText = controller.getDebugStateForIndex(selectedIndex);
        stateView.setText(debugText);
    }

    @FXML
    private void handleFetch() {
        int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
        if (selectedIndex <= 0) {
            new Alert(Alert.AlertType.WARNING, "Please select an existing chat first.").showAndWait();
            return;
        }

        try {
            log.info("GUI: fetchMessages for index {}", selectedIndex);
            List<String> newMessages = controller.fetchMessages(selectedIndex);

            if (newMessages.isEmpty()) {
                messagesView.getItems().add(new Message("system", "No new messages."));
            } else {
                for (String m : newMessages) {
                    messagesView.getItems().add(new Message(controller.getRecipientName(selectedIndex), m));
                }
            }
            String debugText = controller.getDebugStateForIndex(selectedIndex);
            stateView.setText(debugText);
        } catch (RemoteException ex) {
            log.error("Error fetching messages", ex);
            new Alert(Alert.AlertType.ERROR, "Error fetching messages.").showAndWait();
        }
    }

    private void updateChatList() {
        if (chatList != null) {
            chatList.getItems().setAll(controller.getChatNames());
        }
    }

    private void showNewChatDialog() {
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

        TextArea myBumpArea = new TextArea();
        myBumpArea.setEditable(false);
        myBumpArea.setWrapText(true);
        myBumpArea.setPrefRowCount(3);

        Button copyToClipboardButton = new Button("Copy my code");

        TextArea bumpInputArea = new TextArea();
        bumpInputArea.setPromptText("Paste the received code here (name|key|idx|tag)");
        bumpInputArea.setWrapText(true);
        bumpInputArea.setPrefRowCount(3);

        Label statusLabel = new Label("Your code is being generated...");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");

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

        try {
            String myBump = controller.generateOwnBumpString();
            myBumpArea.setText(myBump);
            statusLabel.setText("Copy your code and send it to the other user.");
        } catch (Exception ex) {
            statusLabel.setText("Error generating your code: " + ex.getMessage());
            log.error("Error generating your code", ex);
        }

        copyToClipboardButton.setOnAction(e -> {
            final javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            final javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
            clipboardContent.putString(myBumpArea.getText());
            clipboard.setContent(clipboardContent);
            statusLabel.setText("Code copied to clipboard!");
        });

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
