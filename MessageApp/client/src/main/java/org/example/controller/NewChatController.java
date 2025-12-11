package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewChatController {

    private static final Logger log = LoggerFactory.getLogger(NewChatController.class);

    @FXML
    private TextField chatNameField;
    @FXML
    private TextArea sendKeyDisplay;
    @FXML
    private Button copySendKeyButton;
    @FXML
    private TextArea receiveKeyArea;
    @FXML
    private ImageView headerIcon;
    @FXML
    private ImageView chatIcon;
    @FXML
    private ImageView keyIcon;
    @FXML
    private ImageView generateIcon;
    @FXML
    private ImageView copyIcon;
    @FXML
    private ImageView importIcon;
    @FXML
    private ImageView createIcon;
    @FXML
    private ImageView cancelIcon;

    private ChatCore chatCore;
    private Stage dialogStage;
    private String generatedSendKey;
    private boolean isDarkTheme = false;

    public void setController(ChatCore chatCore) {
        this.chatCore = chatCore;
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        // Detect theme from stylesheets
        if (dialogStage.getScene() != null) {
            isDarkTheme = dialogStage.getScene().getStylesheets().stream()
                    .anyMatch(s -> s.contains("dark.css"));
        }
        updateIcons();
    }

    @FXML
    public void initialize() {
    }

    /**
     * Updates icons based on the current theme.
     */
    private void updateIcons() {
        String theme = isDarkTheme ? "dark_icons" : "light_icons";
        String themedPath = "/org/example/icons/" + theme + "/";
        String colorPath = "/org/example/icons/color_icons/";

        loadIcon(headerIcon, themedPath + "add-user.png");
        loadIcon(chatIcon, themedPath + "chat.png");
        loadIcon(keyIcon, themedPath + "key.png");
        loadIcon(generateIcon, themedPath + "key-button.png");
        loadIcon(copyIcon, themedPath + "copy.png");
        loadIcon(importIcon, themedPath + "import.png");
        loadIcon(createIcon, themedPath + "add.png");
        loadIcon(cancelIcon, colorPath + "cancel-red.png");
    }

    private void loadIcon(ImageView imageView, String path) {
        if (imageView != null) {
            try {
                imageView.setImage(new Image(getClass().getResourceAsStream(path)));
            } catch (Exception e) {
                log.warn("Could not load icon: {}", path);
            }
        }
    }

    @FXML
    private void handleGenerateSendKey() {
        try {
            generatedSendKey = chatCore.generateSendKeyInfo();
            sendKeyDisplay.setText(generatedSendKey);
            copySendKeyButton.setDisable(false);
            log.info("Generated send key");
        } catch (Exception ex) {
            log.error("Error generating send key", ex);
        }
    }

    @FXML
    private void handleCopySendKey() {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(sendKeyDisplay.getText());
        clipboard.setContent(content);
    }

    @FXML
    private void handleCreateChat() {
        String chatName = chatNameField.getText().trim();
        if (chatName.isEmpty()) {
            // Optionally, show an alert to the user
            log.error("Chat name cannot be empty");
            return;
        }

        String receiveKey = receiveKeyArea.getText().trim();
        String sendKey = generatedSendKey;

        // At least one key must be present
        if ((sendKey == null || sendKey.isEmpty()) && receiveKey.isEmpty()) {
            return;
        }

        boolean success = chatCore.createChatWithKeys(chatName, sendKey, receiveKey);

        if (success) {
            // Close dialog after a brief delay
            javafx.application.Platform.runLater(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // ignore
                }
                dialogStage.close();
            });
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}
