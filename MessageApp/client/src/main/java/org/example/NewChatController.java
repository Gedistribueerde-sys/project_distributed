package org.example;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewChatController {

    private static final Logger log = LoggerFactory.getLogger(NewChatController.class);

    @FXML
    private TextArea myBumpArea;
    @FXML
    private Button copyToClipboardButton;
    @FXML
    private TextArea bumpInputArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Button acceptButton;
    @FXML
    private Button cancelButton;

    private Controller controller;
    private Stage dialogStage;
    private boolean accepted = false;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setup() {
        statusLabel.setText("Your code is being generated...");
        try {
            String myBump = controller.generateOwnBumpString();
            myBumpArea.setText(myBump);
            statusLabel.setText("Copy your code and send it to the other user.");
        } catch (Exception ex) {
            statusLabel.setText("Error generating your code: " + ex.getMessage());
            log.error("Error generating your code", ex);
        }
    }

    @FXML
    public void initialize() {
        acceptButton.disableProperty().bind(bumpInputArea.textProperty().isEmpty());
    }

    @FXML
    private void handleCopy() {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(myBumpArea.getText());
        clipboard.setContent(clipboardContent);
        statusLabel.setText("Code copied to clipboard!");
    }

    @FXML
    private void handleAccept() {
        String myCode = myBumpArea.getText().trim();
        String otherCode = bumpInputArea.getText().trim();

        if (otherCode.isEmpty()) {
            statusLabel.setText("Please paste the other user's code first.");
            return;
        }

        if (controller.acceptNewChat(myCode, otherCode)) {
            statusLabel.setText("Chat created successfully!");
            accepted = true;
            dialogStage.close();
        } else {
            statusLabel.setText("Error accepting: check the codes.");
            accepted = false;
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}
