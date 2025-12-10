package org.example;

import org.example.GUI.Message;
import org.example.crypto.ChatCrypto;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

// This class holds the state of a chat with a specific recipient
public class ChatState {
    public String recipient;
    public final String recipientUuid;

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

    public ChatState(String recipient, String recipientUuid, SecretKey sendKey, long sendIdx, String sendTag, SecretKey recvKey, long recvIdx, String recvTag) {
        this.recipient = recipient;
        this.recipientUuid = recipientUuid;

        this.sendKey = sendKey;
        this.sendIdx = sendIdx;
        this.sendTag = sendTag;

        this.recvKey = recvKey;
        this.recvIdx = recvIdx;
        this.recvTag = recvTag;

        this.messages = new ArrayList<>();
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getRecipientUuid() {
        return recipientUuid;
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
            status = " (<->)"; // Two-way
        } else if (canSend()) {
            status = " (->)"; // Send only
        } else if (canReceive()) {
            status = " (<-)"; // Receive only
        }
        
        String shortUuid = recipientUuid;
        if (recipientUuid != null && recipientUuid.length() > 8) {
            shortUuid = recipientUuid.substring(0, 8) + "...";
        }
        
        return recipient + " [" + shortUuid + "]" + status;
    }
}
