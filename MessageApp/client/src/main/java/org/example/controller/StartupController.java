package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import org.example.GUI.GUI;

public class StartupController {

    private GUI gui;

    public void setGui(GUI gui) {
        this.gui = gui;
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
