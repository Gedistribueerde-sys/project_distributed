package org.example;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    private Controller controller;
    private Stage stage;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void handleLogin() {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        if (controller.login(user, pass)) {
            statusLabel.setText("Login successful!");
            // The GUI class will handle the scene change
        } else {
            statusLabel.setText("Login failed");
        }
    }

    @FXML
    private void handleRegister() {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        if (controller.register(user, pass)) {
            statusLabel.setText("Registration successful, please log in now");
        } else {
            statusLabel.setText("Registration failed (name already exists or is invalid)");
        }
    }
}
