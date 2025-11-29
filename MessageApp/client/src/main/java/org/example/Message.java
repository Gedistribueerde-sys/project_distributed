package org.example;

/**
 * Simple message model used by the UI.
 * sender: the username who sent the message
 * text: the message body
 */
public class Message {
    private final String sender;
    private final String text;

    public Message(String sender, String text) {
        this.sender = sender;
        this.text = text;
    }

    public String getSender() { return sender; }
    public String getText() { return text; }
}
