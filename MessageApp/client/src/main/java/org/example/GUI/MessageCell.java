// MessageCell.java
package org.example.GUI;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Duration;

// A custom ListCell for displaying messages in a chat application.
public class MessageCell extends ListCell<Message> {

    private static final double AVATAR_SIZE = 32;
    private static final double MAX_BUBBLE_WIDTH = 420;

    public MessageCell() {
        // Make the cell transparent to show custom styling
        setStyle("-fx-background-color: transparent;");
    }

    // Updates the cell's content based on the Message item.
    @Override
    protected void updateItem(Message item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        boolean outgoing = item.isSent();

        // Create the bubble content container
        VBox bubbleContent = new VBox(2);
        bubbleContent.setMaxWidth(MAX_BUBBLE_WIDTH);

        // Message text
        Label messageLabel = new Label(item.text());
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(MAX_BUBBLE_WIDTH - 24); // Account for padding
        messageLabel.getStyleClass().add("message-text");
        messageLabel.getStyleClass().add(outgoing ? "message-text-outgoing" : "message-text-incoming");

        // Bottom row: timestamp and status
        HBox bottomRow = new HBox(6);
        bottomRow.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // Timestamp
        Label timeLabel = new Label(item.getFormattedTime());
        timeLabel.getStyleClass().add("message-time");
        timeLabel.getStyleClass().add(outgoing ? "message-time-outgoing" : "message-time-incoming");
        bottomRow.getChildren().add(timeLabel);

        // Status indicator for outgoing messages
        if (outgoing) {
            Label statusLabel = new Label(item.status().getIcon());
            statusLabel.getStyleClass().add("message-status");
            statusLabel.getStyleClass().add(getStatusStyleClass(item.status()));

            Tooltip statusTooltip = new Tooltip(item.status().getTooltip());
            statusTooltip.setShowDelay(Duration.millis(200));
            Tooltip.install(statusLabel, statusTooltip);

            bottomRow.getChildren().add(statusLabel);
        }

        bubbleContent.getChildren().addAll(messageLabel, bottomRow);

        // Create the bubble wrapper with styling
        VBox bubble = new VBox(bubbleContent);
        bubble.getStyleClass().add("message-bubble");
        bubble.getStyleClass().add(outgoing ? "outgoing" : "incoming");
        bubble.setPadding(new Insets(8, 12, 6, 12));
        bubble.setMaxWidth(MAX_BUBBLE_WIDTH);

        // Add pending animation for unsent messages
        if (outgoing && item.status() == Message.MessageStatus.PENDING) {
            bubble.getStyleClass().add("pending");
            addPulseEffect(bubble);
        }

        // Main container with avatar
        HBox container = new HBox(8);
        container.getStyleClass().add("message-cell-hbox");
        container.setPadding(new Insets(3, 12, 3, 12));

        if (outgoing) {
            // Outgoing: bubble on right, no avatar
            container.setAlignment(Pos.CENTER_RIGHT);
            container.getChildren().add(bubble);
        } else {
            // Incoming: avatar on left, bubble on right
            container.setAlignment(Pos.CENTER_LEFT);
            StackPane avatar = createAvatar(item.sender());
            container.getChildren().addAll(avatar, bubble);
        }

        // Apply fade-in animation for new messages
        if (getIndex() == getListView().getItems().size() - 1) {
            applyFadeIn(container);
        }

        setText(null);
        setGraphic(container);
    }

    // Creates an avatar with the sender's initial and a consistent background color.
    private StackPane createAvatar(String sender) {
        Circle circle = new Circle(AVATAR_SIZE / 2);
        circle.getStyleClass().add("avatar-circle");

        // Generate a consistent color based on the sender's name
        Color avatarColor = generateAvatarColor(sender);
        circle.setFill(avatarColor);

        // First letter of sender
        String initial = sender != null && !sender.isEmpty()
                ? sender.substring(0, 1).toUpperCase()
                : "?";

        Text initialText = new Text(initial);
        initialText.getStyleClass().add("avatar-text");
        initialText.setFill(Color.WHITE);

        StackPane avatar = new StackPane(circle, initialText);
        avatar.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.setMaxSize(AVATAR_SIZE, AVATAR_SIZE);

        return avatar;
    }

    // Generates a color based on the hash of the sender's name.
    private Color generateAvatarColor(String name) {
        if (name == null || name.isEmpty()) {
            return Color.GRAY;
        }

        // Use hash to generate consistent color
        int hash = name.hashCode();
        double hue = Math.abs(hash % 360);
        return Color.hsb(hue, 0.6, 0.7);
    }

    // Maps message status to corresponding CSS style class.
    private String getStatusStyleClass(Message.MessageStatus status) {
        return switch (status) {
            case PENDING -> "status-pending";
            case SENT -> "status-sent";
            case DELIVERED -> "status-delivered";
        };
    }

    // Applies a fade-in animation to the message container.
    private void applyFadeIn(HBox container) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), container);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    // Adds a subtle pulse effect to indicate a pending message.
    private void addPulseEffect(VBox bubble) {
        bubble.setOpacity(0.85);
    }
}
