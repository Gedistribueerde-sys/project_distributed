package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import org.example.ChatState;
import org.example.GUI.Message;
import org.example.GUI.MessageCell;
import org.example.GUI.GUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
// java.rmi.RemoteException is nu niet meer nodig
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


    private ChatCore chatCore;
    private Stage stage;
    private GUI gui;

    public void setController(ChatCore chatCore) {
        this.chatCore = chatCore;
    }

    public void setGui(GUI gui) {
        this.gui = gui;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        // Custom Cell Factory for Chat List to include Rename Button
        chatList.setCellFactory(lv -> new ListCell<String>() {
            private final HBox content;
            private final Label nameLabel;
            private final Button renameButton;

            {
                nameLabel = new Label();
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);

                renameButton = new Button("✎"); // Pencil icon or "Rename"
                renameButton.setStyle("-fx-font-size: 16px; -fx-padding: 4 8;");
                renameButton.setOnAction(event -> {
                    String item = getItem();
                    if (item != null && getIndex() > 0) { // Index 0 is "New Chat"
                        handleRenameChat(getIndex());
                    }
                });

                content = new HBox(nameLabel, renameButton);
                content.setSpacing(5);
                content.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    nameLabel.setText(item);
                    // Hide rename button for "New Chat" (index 0)
                    if (getIndex() == 0) {
                        renameButton.setVisible(false);
                        renameButton.setManaged(false);
                    } else {
                        renameButton.setVisible(true);
                        renameButton.setManaged(true);
                    }
                    setGraphic(content);
                    setText(null);
                }
            }
        });

        // This is called after the FXML file has been loaded
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex < 0) return;

            messagesView.getItems().clear();


            if (selectedIndex == 0) {
                stateView.clear();
                messagesView.getItems().add(new Message("system", "Use the dialog to start a chat...", true));
                // Hide buttons for new chat
                sendButton.setVisible(false);
                sendButton.setManaged(false);

                messageField.setVisible(false);
                messageField.setManaged(false);

                Platform.runLater(() -> {
                    showNewChatDialog();
                    // chatList is updated in showNewChatDialog if successful
                    chatList.getSelectionModel().clearSelection();
                });
            } else {
                // A chat is selected, update button visibility based on capabilities
                boolean canSend = chatCore.canSendToChat(selectedIndex);
                sendButton.setVisible(canSend);
                sendButton.setManaged(canSend);
                messageField.setVisible(canSend);
                messageField.setManaged(canSend);

                // Immediately fetch messages for the selected chat
                ChatState selectedChat = chatCore.getChatState(selectedIndex);
                if (selectedChat != null) {
                    new Thread(() -> chatCore.getInAndOutBox().fetchMessagesImmediately(selectedChat)).start();
                }

                // Refresh the message view and state
                refreshMessagesView(selectedIndex);
                String debugText = chatCore.getDebugStateForIndex(selectedIndex);
                stateView.setText(debugText);
            }
        });
    }

    private void handleRenameChat(int selectedIndex) {
        if (selectedIndex <= 0) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Rename Chat");
        dialog.setHeaderText("Enter a new name for this chat:");
        dialog.setContentText("Name:");
        dialog.setGraphic(null); // Remove the default question mark icon
        dialog.getDialogPane().setMinWidth(350); // Ensure dialog is wide enough

        // Apply theme to dialog
        if (stage != null && stage.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
        }

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                chatCore.renameChat(selectedIndex, name.trim());
                updateChatList();
                // Reselect to keep focus
                chatList.getSelectionModel().select(selectedIndex);
            }
        });
    }

    public void setup() {
        userLabel.setText("Logged in as: " + chatCore.getCurrentUser());
        // set the custom cell factory now that controller/current user is available
        messagesView.setCellFactory(lv -> new MessageCell());
        updateChatList();
        // register the UI callback in the chatcore/processor

        chatCore.setOnMessageUpdateCallback(() -> { // method call to chatcore
            // refresh the view once a chat is selected
            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex > 0) {
                refreshMessagesView(selectedIndex);
                String debugText = chatCore.getDebugStateForIndex(selectedIndex);
                stateView.setText(debugText);
            }
        });
    }

    @FXML
    private void handleLogout() {
        chatCore.logout();
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

        chatCore.sendMessage(selectedIndex, text);
        refreshMessagesView(selectedIndex);
        messageField.clear();
        String debugText = chatCore.getDebugStateForIndex(selectedIndex);
        stateView.setText(debugText);
    }


    @FXML
    private void handleThemeToggle() {
        gui.toggleTheme();
    }

    private void refreshMessagesView(int selectedIndex) {
        if (selectedIndex <= 0) {
            messagesView.getItems().clear();
            return;
        }
        List<Message> messages = chatCore.getMessagesForChat(selectedIndex);
        messagesView.getItems().setAll(messages);
        messagesView.scrollTo(messages.size() - 1); // Scroll naar het nieuwste bericht
    }

    private void updateChatList() {
        if (chatList != null) {
            chatList.getItems().setAll(chatCore.getChatNames());
        }
    }

    private void showNewChatDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/NewChatView.fxml"));
            VBox page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Create New Chat");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            Scene scene = new Scene(page);
            
            // Apply the same theme as the main window
            scene.getStylesheets().addAll(stage.getScene().getStylesheets());
            
            dialogStage.setScene(scene);

            NewChatController newChatController = loader.getController();
            newChatController.setController(chatCore);
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
