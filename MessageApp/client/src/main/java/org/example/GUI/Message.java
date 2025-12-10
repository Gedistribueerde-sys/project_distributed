package org.example.GUI;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Message model used by the UI with delivery status tracking.
 *
 * @param sender    The username who sent the message
 * @param text      The message body
 * @param isSent    True if this is an outgoing message (sent by current user)
 * @param status    The delivery status of the message
 * @param timestamp The time when the message was created
 */
public record Message(String sender, String text, boolean isSent, MessageStatus status, LocalDateTime timestamp) {

    /**
     * Message delivery status for visual feedback.
     */
    public enum MessageStatus {
        /** Message is queued locally, not yet sent to server */
        PENDING("⏳", "Sending..."),
        /** Message was sent to server successfully */
        SENT("✓", "Sent"),
        /** Message was delivered/read (for received messages) */
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

    /**
     * Backward-compatible constructor for existing code.
     */
    public Message(String sender, String text, boolean isSent) {
        this(sender, text, isSent,
             isSent ? MessageStatus.SENT : MessageStatus.DELIVERED,
             LocalDateTime.now());
    }

    /**
     * Constructor with status but auto-timestamp.
     */
    public Message(String sender, String text, boolean isSent, MessageStatus status) {
        this(sender, text, isSent, status, LocalDateTime.now());
    }

    /**
     * Returns a formatted time string for display.
     */
    public String getFormattedTime() {
        if (timestamp == null) return "";
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}
