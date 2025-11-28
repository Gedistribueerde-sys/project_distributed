package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class GUI extends Application {
    private static final Logger log = LoggerFactory.getLogger(GUI.class);

    private Controller controller;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.controller = Client.controller;
        this.stage = stage;

        stage.setTitle("Message App");
        showLoginScene();
        stage.show();
    }

    private void showLoginScene() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/LoginView.fxml"));
            Parent root = loader.load();

            LoginController loginController = loader.getController();
            loginController.setController(controller);
            loginController.setStage(stage);

            // Add a listener to the controller's login status
            controller.loggedInProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    showChatScene();
                }
            });

            Scene scene = new Scene(root, 400, 250);
            stage.setScene(scene);
        } catch (IOException e) {
            log.error("Failed to load LoginView.fxml", e);
        }
    }

    private void showChatScene() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/ChatView.fxml"));
            Parent root = loader.load();

            ChatController chatController = loader.getController();
            chatController.setController(controller);
            chatController.setStage(stage);
            chatController.setup();

            // Add a listener to the controller's logout status
            controller.loggedOutProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    showLoginScene();
                }
            });

            Scene scene = new Scene(root, 900, 600);
            stage.setScene(scene);
        } catch (IOException e) {
            log.error("Failed to load ChatView.fxml", e);
        }
    }
}
