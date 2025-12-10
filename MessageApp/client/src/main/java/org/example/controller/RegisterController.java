package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

    @FXML
    private ImageView pageIcon;

    @FXML
    private ImageView userFieldIcon;

    @FXML
    private ImageView keyFieldIcon;

    @FXML
    private ImageView confirmKeyIcon;

    @FXML
    private ImageView addUserIcon;

    private ChatCore chatCore;
    private GUI gui;

    @FXML
    private void initialize() {
        // Add event handlers for Enter key press on text fields
        usernameField.setOnKeyPressed(this::handleEnterKey);
        passwordField.setOnKeyPressed(this::handleEnterKey);
        confirmPasswordField.setOnKeyPressed(this::handleEnterKey);
    }

    private void handleEnterKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleRegister();
            event.consume(); // Consume the event to prevent it from being processed further
        }
    }

    public void setController(ChatCore chatCore) {
        this.chatCore = chatCore;
    }

    public void setGui(GUI gui) {
        this.gui = gui;
        updateIcons();
    }

    /**
     * Updates icons based on the current theme.
     */
    private void updateIcons() {
        if (gui == null) return;
        String theme = gui.isDarkTheme() ? "dark" : "light";
        String themedPath = "/org/example/icons/" + theme + "/";
        String colorfulPath = "/org/example/icons/";

        // Secure icon is colorful, works on any background
        loadIcon(pageIcon, themedPath + "add-user-large.png");
        loadIcon(userFieldIcon, colorfulPath + "user.png");
        loadIcon(keyFieldIcon, themedPath + "key.png");
        loadIcon(confirmKeyIcon, themedPath + "key.png");
        loadIcon(addUserIcon, colorfulPath + "add-user-white.png");
    }

    private void loadIcon(ImageView imageView, String path) {
        if (imageView != null) {
            try {
                imageView.setImage(new Image(getClass().getResourceAsStream(path)));
            } catch (Exception e) {
                // Icon loading failed, leave empty
            }
        }
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
            statusLabel.setText("Registration successful!");
            gui.showLoginScene(); // Jump to login screen
        } else {
            statusLabel.setText("Registration failed (name already exists or is invalid)");
        }
    }

    @FXML
    private void handleBackButtonAction(ActionEvent event) {
        gui.showStartupScene();
    }
}
