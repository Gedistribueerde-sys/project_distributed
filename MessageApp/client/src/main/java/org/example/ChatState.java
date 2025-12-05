package org.example;

import org.example.GUI.Message;
import org.example.crypto.ChatCrypto;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

// This class holds the state of a chat with a specific recipient
public class ChatState {
    public final String recipient;

    // Sending capability (nullable if receive-only)
    public SecretKey sendKey;
    public long sendIdx;
    public String sendTag;

    // Receiving capability (nullable if send-only)
    public SecretKey recvKey;
    public long recvIdx;
    public String recvTag;

    // In-memory message storage
    private final List<Message> messages;

    // Backoff timestamp for handling poison messages.
    public long poisonedBackoffUntil = 0;

    public ChatState(String recipient, SecretKey sendKey, long sendIdx, String sendTag, SecretKey recvKey, long recvIdx, String recvTag) {
        this.recipient = recipient;

        this.sendKey = sendKey;
        this.sendIdx = sendIdx;
        this.sendTag = sendTag;

        this.recvKey = recvKey;
        this.recvIdx = recvIdx;
        this.recvTag = recvTag;

        this.messages = new ArrayList<>();
    }

    public boolean canSend() {
        return sendKey != null;
    }

    public boolean canReceive() {
        return recvKey != null;
    }

    /**
     * @return True if the chat is in a temporary backoff state due to a poison message.
     */
    public boolean isPoisoned() {
        return System.currentTimeMillis() < poisonedBackoffUntil;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void addSentMessage(String content, String sender) {
        messages.add(new Message(sender, content, true));
    }

    public void addReceivedMessage(String content) {
        messages.add(new Message(recipient, content, false));
    }

    @Override
    public String toString() {
        String status = "";
        if (canSend() && canReceive()) {
            status = " (⇄)"; // Two-way
        } else if (canSend()) {
            status = " (→)"; // Send only
        } else if (canReceive()) {
            status = " (←)"; // Receive only
        }
        return "Chat with " + recipient + status;
    }

    // This is for debug purposes only
    public String debugInfo() {
        StringBuilder sb = new StringBuilder();

        if (canSend()) {
            sb.append("OUT (you → ").append(recipient).append(")\n").append(" key = ").append(ChatCrypto.keyToBase64(sendKey)).append("\n").append(" idx = ").append(sendIdx).append("\n").append(" tag = ").append(sendTag).append("\n\n");
        } else {
            sb.append("OUT: Not available (receive-only chat)\n\n");
        }

        if (canReceive()) {
            sb.append("IN (").append(recipient).append(" → you)\n").append(" key = ").append(ChatCrypto.keyToBase64(recvKey)).append("\n").append(" idx = ").append(recvIdx).append("\n").append(" tag = ").append(recvTag);
        } else {
            sb.append("IN: Not available (send-only chat)");
        }

        return sb.toString();
    }
}
