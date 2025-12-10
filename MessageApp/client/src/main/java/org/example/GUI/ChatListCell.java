package org.example.GUI;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

import java.util.function.Consumer;

/**
 * Custom cell for the chat list with modern styling.
 * Features:
 * - Avatar with initials
 * - Chat name
 * - Connection status indicator (send/receive capability)
 * - Rename button
 */
public class ChatListCell extends ListCell<String> {

    private static final double AVATAR_SIZE = 40;

    private final HBox container;
    private final StackPane avatar;
    private final Circle avatarCircle;
    private final Text avatarInitial;
    private final VBox textContainer;
    private final Label nameLabel;
    private final Label statusLabel;
    private final Button renameButton;
    private final Consumer<Integer> onRenameAction;

    /**
     * Creates a new chat list cell.
     *
     * @param onRenameAction Callback when rename button is clicked, receives the cell index
     */
    public ChatListCell(Consumer<Integer> onRenameAction) {
        this.onRenameAction = onRenameAction;

        // Avatar
        avatarCircle = new Circle(AVATAR_SIZE / 2);
        avatarCircle.getStyleClass().add("chat-avatar-circle");

        avatarInitial = new Text();
        avatarInitial.getStyleClass().add("chat-avatar-text");
        avatarInitial.setFill(Color.WHITE);

        avatar = new StackPane(avatarCircle, avatarInitial);
        avatar.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.setMaxSize(AVATAR_SIZE, AVATAR_SIZE);

        // Text content
        nameLabel = new Label();
        nameLabel.getStyleClass().add("chat-name-label");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("chat-status-label");

        textContainer = new VBox(2);
        textContainer.getChildren().addAll(nameLabel, statusLabel);
        textContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        // Rename button
        renameButton = new Button("✎");
        renameButton.getStyleClass().add("chat-rename-button");
        renameButton.setOnAction(event -> {
            if (onRenameAction != null && getIndex() > 0) {
                onRenameAction.accept(getIndex());
            }
        });

        // Container
        container = new HBox(12);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(8, 12, 8, 12));
        container.getChildren().addAll(avatar, textContainer, renameButton);

        // Listen for selection changes to update text colors
        selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            updateTextColors(isSelected);
        });
    }

    /**
     * Updates text colors based on selection state.
     * Uses CSS classes so the theme-specific CSS files can control the actual colors.
     */
    private void updateTextColors(boolean isSelected) {
        if (isSelected) {
            // Selected: white text on colored background
            nameLabel.getStyleClass().removeAll("chat-name-unselected");
            statusLabel.getStyleClass().removeAll("chat-status-unselected");

            nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 14px;");
            statusLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 12px;");
            renameButton.setStyle("-fx-text-fill: rgba(255,255,255,0.8); -fx-background-color: transparent;");
        } else {
            // Not selected: use CSS class for theme-aware colors
            nameLabel.getStyleClass().add("chat-name-unselected");
            statusLabel.getStyleClass().add("chat-status-unselected");

            // Clear inline styles to let CSS take over
            nameLabel.setStyle("");
            statusLabel.setStyle("");
            renameButton.setStyle("");
        }
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        int index = getIndex();

        if (index == 0) {
            // "New Chat" item - special styling
            setupNewChatItem();
        } else {
            // Regular chat item
            setupRegularChatItem(item);
        }

        // Ensure colors are refreshed when cells are reused by the ListView.
        updateTextColors(isSelected());

        setText(null);
        setGraphic(container);
    }

    private void setupNewChatItem() {
        // Special avatar for "New Chat"
        avatarCircle.setFill(Color.web("#10b981")); // Green
        avatarInitial.setText("+");

        nameLabel.setText("New Chat");
        statusLabel.setText("Start a new conversation");

        // Hide rename button
        renameButton.setVisible(false);
        renameButton.setManaged(false);
    }

    private void setupRegularChatItem(String chatName) {
        // Parse chat name to extract display name and status
        String displayName = chatName;
        String statusText = "";

        // Check for connection indicators in the name
        if (chatName.contains("(<->)")) {
            statusText = "↔ Send & Receive";
            displayName = chatName.replace("(<->)", "").trim();
        } else if (chatName.contains("(->)")) {
            statusText = "→ Send only";
            displayName = chatName.replace("(->)", "").trim();
        } else if (chatName.contains("(<-)")) {
            statusText = "← Receive only";
            displayName = chatName.replace("(<-)", "").trim();
        }

        // Remove UUID part if present
        if (displayName.contains("[")) {
            displayName = displayName.substring(0, displayName.indexOf("[")).trim();
        }

        // Generate avatar color from name
        Color avatarColor = generateAvatarColor(displayName);
        avatarCircle.setFill(avatarColor);

        // Set initial
        String initial = displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase();
        avatarInitial.setText(initial);

        // Set labels
        nameLabel.setText(displayName);
        statusLabel.setText(statusText);

        // Show rename button
        renameButton.setVisible(true);
        renameButton.setManaged(true);
    }

    /**
     * Generates a consistent color for an avatar based on the name.
     */
    private Color generateAvatarColor(String name) {
        if (name == null || name.isEmpty()) {
            return Color.GRAY;
        }

        // Use hash to generate consistent color
        int hash = name.hashCode();
        double hue = Math.abs(hash % 360);
        return Color.hsb(hue, 0.55, 0.65);
    }
}

