package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import org.example.ChatState;
import org.example.GUI.ChatListCell;
import org.example.GUI.Message;
import org.example.GUI.MessageCell;
import org.example.GUI.GUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ChatController {
    // logger to log info / errors
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // fxml elements, see fxml files
    @FXML
    private ListView<String> chatList;
    @FXML
    private Label userLabel;
    @FXML
    private ListView<Message> messagesView;
    @FXML
    private TextField messageField;
    @FXML
    private Button sendButton;
    @FXML
    private HBox chatHeader;
    @FXML
    private Label recipientLabel;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private ImageView userIcon;
    @FXML
    private ImageView themeIcon;
    @FXML
    private ImageView logoutIcon;
    @FXML
    private ImageView sendIcon;

    private ChatCore chatCore;  // Chat Core contains the main logic of the application
    private Stage stage; // Main application stage
    private GUI gui; // GUI manager for theme and scene changes

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
        // ChatListCell is a custom cell factory for the chat list
        chatList.setCellFactory(lv -> new ChatListCell(this::handleRenameChat));

        // This listener handles chat selection changes, dynamically updating the UI
        chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex < 0) return;

            messagesView.getItems().clear();

            if (selectedIndex == 0) {
                messagesView.getItems().add(new Message("system", "Use the dialog to start a chat...", true));
                // Hide buttons for new chat
                sendButton.setVisible(false);
                sendButton.setManaged(false);

                // Hide message field
                messageField.setVisible(false);
                messageField.setManaged(false);

                // Hide chat header
                chatHeader.setVisible(false);
                chatHeader.setManaged(false);

                // Clear active chat for fast polling
                chatCore.setActiveChatUuid(null);

                // run later is a function that runs the code on the JavaFX application thread
                Platform.runLater(() -> {
                    showNewChatDialog();
                    // chatList is updated in showNewChatDialog if successful
                    chatList.getSelectionModel().clearSelection();
                });
            } else {
                // canSendToChat checks if sending is allowed in this chat
                boolean canSend = chatCore.canSendToChat(selectedIndex);

                // Show or hide send button and message field based on kind of chat
                sendButton.setVisible(canSend);
                sendButton.setManaged(canSend);

                // Show or hide message field based on kind of chat
                messageField.setVisible(canSend);
                messageField.setManaged(canSend);

                // Immediately fetch messages for the selected chat
                ChatState selectedChat = chatCore.getChatState(selectedIndex);
                if (selectedChat != null) {
                    // Set this chat as active, active chats are polled more frequently
                    chatCore.setActiveChatUuid(selectedChat.getRecipientUuid());

                    // Thread to fetch messages of the selected chat
                    new Thread(() -> chatCore.getInAndOutBox().fetchMessagesImmediately(selectedChat)).start();

                    // Update and show chat header
                    updateChatHeader(selectedChat);
                }

                // Refresh the message view and state
                refreshMessagesView(selectedIndex);
            }
        });
    }

    // Rename the name of a chat
    private void handleRenameChat(int selectedIndex) {
        if (selectedIndex <= 0) return;

        // Build a dialog to request the new name
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

        // Show the dialog and process the result
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                chatCore.renameChat(selectedIndex, name.trim());
                updateChatList();
                // Reselect to keep focus
                chatList.getSelectionModel().select(selectedIndex);
            }
        });
    }

    // Setup method to initialize the controller after dependencies are set
    public void setup() {
        userLabel.setText(chatCore.getCurrentUser());
        messagesView.setCellFactory(lv -> new MessageCell());
        updateChatList();
        // Load icons for current theme
        updateIcons();

        // register the UI callback in the chatcore/processor
        chatCore.setOnMessageUpdateCallback(() -> { // method call to chatcore
            // refresh the view once a chat is selected
            int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
            if (selectedIndex > 0) {
                refreshMessagesView(selectedIndex);
            }
        });
    }

    // Update icons based on the current theme
    public void updateIcons() {
        String theme = gui.isDarkTheme() ? "dark_icons" : "light_icons";
        String themedPath = "/org/example/icons/" + theme + "/";
        String colorfulPath = "/org/example/icons/color_icons/";
        
        // User icon is colorful, works on any background
        if (userIcon != null) {
            userIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(colorfulPath + "user.png"))));
        }
        if (themeIcon != null) {
            themeIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(themedPath + "toggle-theme.png"))));
        }
        if (logoutIcon != null) {
            logoutIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(colorfulPath + "logout-red.png"))));
        }
        if (sendIcon != null) {
            sendIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(themedPath + "send.png"))));
        }
    }

    @FXML
    private void handleLogout() {
        chatCore.logout();
    }

    @FXML
    private void handleSend() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        // Get the selected chat index
        int selectedIndex = chatList.getSelectionModel().getSelectedIndex();
        if (selectedIndex <= 0) {
            new Alert(Alert.AlertType.WARNING, "Please select an existing chat first.").showAndWait();
            return;
        }

        // Send the message via chat core
        chatCore.sendMessage(selectedIndex, text);
        refreshMessagesView(selectedIndex);
        messageField.clear();
    }


    @FXML
    private void handleThemeToggle() {
        gui.toggleTheme();
        updateIcons();
    }

    // Refreshes the messages view for the selected chat
    private void refreshMessagesView(int selectedIndex) {
        if (selectedIndex <= 0) {
            messagesView.getItems().clear();
            return;
        }
        List<Message> messages = chatCore.getMessagesForChat(selectedIndex);
        messagesView.getItems().setAll(messages);
        messagesView.scrollTo(messages.size() - 1); // Scroll to the latest message
    }

    // Updates the chat header with recipient name and connection status
    private void updateChatHeader(ChatState chat) {
        if (chat == null) {
            chatHeader.setVisible(false);
            chatHeader.setManaged(false);
            return;
        }

        // Show the header
        chatHeader.setVisible(true);
        chatHeader.setManaged(true);

        // Set recipient name
        recipientLabel.setText(chat.recipient);

        // Set connection status
        String status;
        if (chat.canSend() && chat.canReceive()) {
            status = "↔ Two-way chat";
        } else if (chat.canSend()) {
            status = "→ Send only";
        } else if (chat.canReceive()) {
            status = "← Receive only";
        } else {
            status = "⚠ No connection";
        }
        connectionStatusLabel.setText(status);
    }

    // Updates the chat list from the chat core
    private void updateChatList() {
        if (chatList != null) {
            chatList.getItems().setAll(chatCore.getChatNames());
        }
    }

    // Shows the dialog for creating a new chat
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
