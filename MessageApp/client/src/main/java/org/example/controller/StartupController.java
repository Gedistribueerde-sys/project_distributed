package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.example.GUI.GUI;

public class StartupController {

    @FXML
    private ImageView appIcon;

    @FXML
    private ImageView loginIcon;

    @FXML
    private ImageView registerIcon;

    @FXML
    private ImageView secureIcon;

    private GUI gui;

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

        // Chat and secure icons are colorful, work on any background
        loadIcon(appIcon, colorfulPath + "secure-large.png");
        loadIcon(loginIcon, colorfulPath + "user.png");
        loadIcon(registerIcon, themedPath + "add-user.png");
        loadIcon(secureIcon, colorfulPath + "secure.png");
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
    private void handleLoginButtonAction(ActionEvent event) {
        gui.showLoginScene();
    }

    @FXML
    private void handleRegisterButtonAction(ActionEvent event) {
        gui.showRegisterScene();
    }
}
