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

    public SecretKey sendKey;
    public long sendIdx;
    public String sendTag;

    public SecretKey recvKey;
    public long recvIdx;
    public String recvTag;

    private final List<Message> messages;


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


    public boolean isPoisoned() {
        return System.currentTimeMillis() < poisonedBackoffUntil;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void addSentMessage(String content, String sender) {
        messages.add(new Message(sender, content, true, Message.MessageStatus.PENDING));
    }

    public void addReceivedMessage(String content) {
        messages.add(new Message(recipient, content, false, Message.MessageStatus.DELIVERED));
    }

    @Override
    public String toString() {
        String status = "";
        if (canSend() && canReceive()) {
            status = " (<->)";
        } else if (canSend()) {
            status = " (->)";
        } else if (canReceive()) {
            status = " (<-)";
        }
        
        String shortUuid = recipientUuid;
        if (recipientUuid != null && recipientUuid.length() > 8) {
            shortUuid = recipientUuid.substring(0, 8) + "...";
        }
        
        return recipient + " [" + shortUuid + "]" + status;
    }
}
