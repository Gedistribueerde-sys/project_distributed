package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.example.GUI.GUI;

public class RegisterController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label statusLabel;

    private ChatCore chatCore;
    private GUI gui;

    public void setController(ChatCore chatCore) {
        this.chatCore = chatCore;
    }

    public void setGui(GUI gui) {
        this.gui = gui;
    }

    @FXML
    private void handleRegister() {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        String confirmPass = confirmPasswordField.getText();

        if (!pass.equals(confirmPass)) {
            statusLabel.setText("Passwords do not match.");
            return;
        }

        if (chatCore.register(user, pass)) {
            statusLabel.setText("Registration successful, please log in now.");
        } else {
            statusLabel.setText("Registration failed (name already exists or is invalid)");
        }
    }

    @FXML
    private void handleBackButtonAction(ActionEvent event) {
        gui.showStartupScene();
    }
}
