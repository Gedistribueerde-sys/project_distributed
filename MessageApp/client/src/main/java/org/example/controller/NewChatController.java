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
    private TextField nameField;
    @FXML
    private TextArea sendKeyDisplay;
    @FXML
    private Button generateSendKeyButton;
    @FXML
    private Button copySendKeyButton;
    @FXML
    private TextArea receiveKeyArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Button createChatButton;
    @FXML
    private Button cancelButton;

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
        // Disable create button if name field is empty
        createChatButton.disableProperty().bind(nameField.textProperty().isEmpty());
        statusLabel.setText("Enter a name and generate/paste keys as needed.");
    }

    @FXML
    private void handleGenerateSendKey() {
        try {
            generatedSendKey = chatCore.generateSendKeyInfo();
            sendKeyDisplay.setText(generatedSendKey);
            copySendKeyButton.setDisable(false);
            statusLabel.setText("Send key generated! Share this with your chat partner.");
            log.info("Generated send key");
        } catch (Exception ex) {
            statusLabel.setText("Error generating send key: " + ex.getMessage());
            log.error("Error generating send key", ex);
        }
    }

    @FXML
    private void handleCopySendKey() {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(sendKeyDisplay.getText());
        clipboard.setContent(content);
        statusLabel.setText("Send key copied to clipboard!");
    }

    @FXML
    private void handleCreateChat() {
        String name = nameField.getText().trim();
        String receiveKey = receiveKeyArea.getText().trim();
        String sendKey = generatedSendKey;

        if (name.isEmpty()) {
            statusLabel.setText("Please enter a name for this chat.");
            return;
        }

        // At least one key must be present
        if ((sendKey == null || sendKey.isEmpty()) && receiveKey.isEmpty()) {
            statusLabel.setText("Generate a send key or paste a receive key (or both).");
            return;
        }

        boolean success = chatCore.createChatWithKeys(name, sendKey, receiveKey);

        if (success) {
            statusLabel.setText("Chat created successfully!");
            // Close dialog after a brief delay
            javafx.application.Platform.runLater(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // ignore
                }
                dialogStage.close();
            });
        } else {
            statusLabel.setText("Error creating chat. Check the keys or if chat already exists.");
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}
