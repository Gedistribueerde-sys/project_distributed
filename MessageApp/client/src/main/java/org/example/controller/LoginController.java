package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.example.GUI.GUI;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    @FXML
    private ImageView pageIcon;

    @FXML
    private ImageView userFieldIcon;

    @FXML
    private ImageView keyFieldIcon;

    private ChatCore chatCore;
    private GUI gui;

    public void setController(ChatCore chatCore) {
        this.chatCore = chatCore;
    }

    public void setGui(GUI gui) {
        this.gui = gui;
        updateIcons();
    }

    @FXML
    public void initialize() {
    }

    /**
     * Updates icons based on the current theme.
     */
    private void updateIcons() {
        if (gui == null) return;
        String theme = gui.isDarkTheme() ? "dark_icons" : "light_icons";
        String themedPath = "/org/example/icons/" + theme + "/";
        String colorfulPath = "/org/example/icons/color_icons/";

        // User icon is colorful, works on any background
        loadIcon(pageIcon, colorfulPath + "user-large.png");
        loadIcon(userFieldIcon, colorfulPath + "user.png");
        loadIcon(keyFieldIcon, themedPath + "key.png");
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
    private void handleLogin() {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        if (chatCore.login(user, pass)) {
            statusLabel.setText("Login successful!");
            // The GUI class will handle the scene change
        } else {
            statusLabel.setText("Login failed");
        }
    }

    @FXML
    private void handleBackButtonAction(ActionEvent event) {
        gui.showStartupScene();
    }
}
