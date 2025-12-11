package org.example.GUI;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Represents a chat message with sender, text, status, and timestamp.
public record Message(String sender, String text, boolean isSent, MessageStatus status, LocalDateTime timestamp) {

    // Enumeration for message status with associated icon and tooltip.
    public enum MessageStatus {
        // Message is being sent
        PENDING("⏳", "Sending..."),
        // Message was sent (for sent messages)
        SENT("✓", "Sent"),
        // Message was delivered (for received messages)
        DELIVERED("✓✓", "Delivered");

        private final String icon;
        private final String tooltip;

        MessageStatus(String icon, String tooltip) {
            this.icon = icon;
            this.tooltip = tooltip;
        }

        public String getIcon() {
            return icon;
        }

        public String getTooltip() {
            return tooltip;
        }
    }


    // Constructor with auto-determined status and timestamp.
    public Message(String sender, String text, boolean isSent) {
        this(sender, text, isSent,
             isSent ? MessageStatus.SENT : MessageStatus.DELIVERED,
             LocalDateTime.now());
    }

    // Constructor with specified timestamp.
    public Message(String sender, String text, boolean isSent, MessageStatus status) {
        this(sender, text, isSent, status, LocalDateTime.now());
    }

    // Returns the formatted time string (HH:mm) of the message timestamp.
    public String getFormattedTime() {
        if (timestamp == null) return "";
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}
