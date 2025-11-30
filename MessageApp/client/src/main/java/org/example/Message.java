package org.example;

/**
 * Simple message model used by the UI.
 * sender: the username who sent the message
 * text: the message body
 */
public record Message(String sender, String text) {
}
