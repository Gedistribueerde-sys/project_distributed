package org.example;

import javax.crypto.SecretKey;
import java.util.Base64;

// Hulpklasse om de staat van één chat op te slaan (beide richtingen)
public class ChatState {
    public final String recipient;

    // Richting: jij -> recipient
    public SecretKey sendKey;
    public long sendIdx;
    public String sendTag;

    // Richting: recipient -> jij
    public SecretKey recvKey;
    public long recvIdx;
    public String recvTag;

    public ChatState(String recipient,
                     SecretKey sendKey, long sendIdx, String sendTag,
                     SecretKey recvKey, long recvIdx, String recvTag) {

                this.recipient = recipient;

        this.sendKey = sendKey;
        this.sendIdx = sendIdx;
        this.sendTag = sendTag;

        this.recvKey = recvKey;
        this.recvIdx = recvIdx;
        this.recvTag = recvTag;
    }

    @Override
    public String toString() {
        return "Chat met " + recipient;
    }

    // Handige debug-info voor de GUI
    public String debugInfo() {
        return "OUT (jij → " + recipient + ")\n" +
                " key = " + keyToBase64(sendKey) + "\n" +
                " idx = " + sendIdx + "\n" +
                " tag = " + sendTag + "\n\n" +
                "IN (" + recipient + " → jij)\n" +
                " key = " + keyToBase64(recvKey) + "\n" +
                " idx = " + recvIdx + "\n" +
                " tag = " + recvTag;
    }

    private String keyToBase64(SecretKey key) {
        if (key == null || key.getEncoded() == null) return "-";
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
