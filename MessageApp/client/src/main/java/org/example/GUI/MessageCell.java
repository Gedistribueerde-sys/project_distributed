// MessageCell.java
package org.example.GUI;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;

/**
 * Custom ListCell that renders a Message as a chat bubble.
 * It uses CSS classes defined in styles.css: .message-bubble, .incoming, .outgoing, .message-cell-hbox, .left, .right
 */
public class MessageCell extends ListCell<Message> {
    private final String currentUser;

    public MessageCell(String currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    protected void updateItem(Message item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        boolean outgoing = currentUser != null && currentUser.equals(item.sender());

        Label bubble = new Label(item.text());
        bubble.setWrapText(true);
        bubble.getStyleClass().add("message-bubble");
        bubble.getStyleClass().add(outgoing ? "outgoing" : "incoming");

        HBox hbox = new HBox(bubble);
        hbox.getStyleClass().add("message-cell-hbox");
        hbox.getStyleClass().add(outgoing ? "right" : "left");
        hbox.setPadding(new Insets(4, 8, 4, 8));
        hbox.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        setText(null);
        setGraphic(hbox);
    }
}
