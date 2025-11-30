package org.example;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    @FXML
    private Button sendButton;
    @FXML
    private Button fetchButton;

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
                // Hide buttons for new chat
                sendButton.setVisible(false);
                sendButton.setManaged(false);
                fetchButton.setVisible(false);
                fetchButton.setManaged(false);
                messageField.setVisible(false);
                messageField.setManaged(false);

                Platform.runLater(() -> {
                    showNewChatDialog();
                    // chatList is updated in showNewChatDialog if successful
                    chatList.getSelectionModel().clearSelection();
                });
            } else {
                // A chat is selected, update button visibility based on capabilities
                boolean canSend = controller.canSendToChat(selectedIndex);
                boolean canReceive = controller.canReceiveFromChat(selectedIndex);

                sendButton.setVisible(canSend);
                sendButton.setManaged(canSend);
                messageField.setVisible(canSend);
                messageField.setManaged(canSend);

                fetchButton.setVisible(canReceive);
                fetchButton.setManaged(canReceive);

                // Refresh the message view and state
                refreshMessagesView(selectedIndex);
                String debugText = controller.getDebugStateForIndex(selectedIndex);
                stateView.setText(debugText);
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
        refreshMessagesView(selectedIndex);
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
            boolean newMessages = controller.fetchMessages(selectedIndex);

            if (!newMessages) {
                messagesView.getItems().add(new Message("system", "No new messages."));
            } else {
                refreshMessagesView(selectedIndex);
            }
            String debugText = controller.getDebugStateForIndex(selectedIndex);
            stateView.setText(debugText);
        } catch (RemoteException ex) {
            log.error("Error fetching messages", ex);
            new Alert(Alert.AlertType.ERROR, "Error fetching messages.").showAndWait();
        }
    }

    private void refreshMessagesView(int selectedIndex) {
        if (selectedIndex <= 0) {
            messagesView.getItems().clear();
            return;
        }
        List<Message> messages = controller.getMessagesForChat(selectedIndex);
        messagesView.getItems().setAll(messages);
    }

    private void updateChatList() {
        if (chatList != null) {
            chatList.getItems().setAll(controller.getChatNames());
        }
    }

    private void showNewChatDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("NewChatView.fxml"));
            VBox page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Create New Chat");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            NewChatController newChatController = loader.getController();
            newChatController.setController(controller);
            newChatController.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            // Always update chat list after dialog closes (in case chat was created)
            updateChatList();

        } catch (IOException e) {
            log.error("Failed to load new chat dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the new chat dialog.").showAndWait();
        }
    }
}
