package org.example.GUI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.Client;
import org.example.controller.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class GUI extends Application {
    private static final Logger log = LoggerFactory.getLogger(GUI.class);

    private ChatCore chatCore;
    private Stage stage;
    private boolean isDarkTheme = false;
    private final String darkThemeUrl = Objects.requireNonNull(getClass().getResource("/org/example/dark.css")).toExternalForm();

    @Override
    public void start(Stage stage) {
        this.chatCore = Client.chatCore;
        this.stage = stage;

        stage.setTitle("Message App");

        // Ensure the application shuts down gracefully when the window is closed.
        // This will trigger the JVM shutdown hook in ChatCore.
        stage.setOnCloseRequest(event -> {
            log.info("Window close requested. Initiating application shutdown.");
            Platform.exit();
            System.exit(0);
        });

        showStartupScene();
        stage.show();
    }

    // Show the startup scene, this is the first scene displayed
    public void showStartupScene() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/StartupView.fxml"));
            Parent root = loader.load();

            StartupController startupController = loader.getController();
            startupController.setGui(this);

            Scene scene = new Scene(root, 500, 500);
            applyTheme(scene);
            stage.setScene(scene);
        } catch (IOException e) {
            log.error("Failed to load StartupView.fxml", e);
        }
    }

    // Show the login scene
    public void showLoginScene() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/LoginView.fxml"));
            Parent root = loader.load();

            LoginController loginController = loader.getController();
            loginController.setController(chatCore);
            loginController.setGui(this);

            // Add a listener to the controller's login status
            chatCore.loggedInProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    showChatScene();
                }
            });

            Scene scene = new Scene(root, 500, 500);
            applyTheme(scene);
            stage.setScene(scene);
        } catch (IOException e) {
            log.error("Failed to load LoginView.fxml", e);
        }
    }

    // Show the registration scene
    public void showRegisterScene() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/RegisterView.fxml"));
            Parent root = loader.load();

            RegisterController registerController = loader.getController();
            registerController.setController(chatCore);
            registerController.setGui(this);

            Scene scene = new Scene(root, 500, 500);
            applyTheme(scene);
            stage.setScene(scene);
        } catch (IOException e) {
            log.error("Failed to load RegisterView.fxml", e);
        }
    }

    // Show the main chat scene
    private void showChatScene() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/ChatView.fxml"));
            Parent root = loader.load();

            ChatController chatController = loader.getController();
            chatController.setController(chatCore);
            chatController.setGui(this);
            chatController.setStage(stage);
            chatController.setup();

            // Add a listener to the controller's logout status
            chatCore.loggedOutProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    showLoginScene();
                }
            });

            Scene scene = new Scene(root, 900, 600);
            applyTheme(scene);
            stage.setScene(scene);
        } catch (IOException e) {
            log.error("Failed to load ChatView.fxml", e);
        }
    }

    // Apply the current theme to the given scene
    private void applyTheme(Scene scene) {
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/org/example/light.css")).toExternalForm());
        if (isDarkTheme) {
            scene.getStylesheets().add(darkThemeUrl);
        }
    }

    // Toggle between light and dark themes
    public void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        Scene scene = stage.getScene();
        if (scene != null) {
            scene.getStylesheets().clear();
            applyTheme(scene);
        }
    }

    public boolean isDarkTheme() {
        return isDarkTheme;
    }
}

