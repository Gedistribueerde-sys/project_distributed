package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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

    private ChatCore chatCore;
    private Stage dialogStage;
    private String generatedSendKey;

    public void setController(ChatCore chatCore) {
        this.chatCore = chatCore;
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    public void initialize() {
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
