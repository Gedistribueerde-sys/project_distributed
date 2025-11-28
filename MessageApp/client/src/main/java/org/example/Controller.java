package org.example;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Controller {
    private static final Logger log = LoggerFactory.getLogger(Controller.class);
    private final List<ChatState> activeChats = new ArrayList<>();
    private final KeyStoreImpl keyStore = new KeyStoreImpl();
    private String currentUser;
    private final BulletinBoard bulletinBoard;

    private final BooleanProperty loggedIn = new SimpleBooleanProperty(false);
    private final BooleanProperty loggedOut = new SimpleBooleanProperty(false);

    public Controller(BulletinBoard bulletinBoard) {this.bulletinBoard = bulletinBoard;}

    public BooleanProperty loggedInProperty() {
        return loggedIn;
    }

    public BooleanProperty loggedOutProperty() {
        return loggedOut;
    }

    public boolean register(String username, String password) {

        boolean created = keyStore.makeKeyStore(username, password);
        if (created) {
            log.info("Keystore created for user: {}", username);
        } else {
            log.info("Failed to create keystore for user: {}", username);
        }
        return created;
    }

    public boolean login(String username, String password) {
        boolean loaded = keyStore.loadKeyStore(username, password);
        if (loaded) {
            currentUser = username;
            log.info("User {} logged in successfully.", username);
            loggedIn.set(true);
            loggedOut.set(false);
        } else {
            log.info("Failed login attempt for user: {}", username);
            loggedIn.set(false);
        }
        return loaded;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void logout() {
        log.info("User {} logged out.", currentUser);
        currentUser = null;
        loggedIn.set(false);
        loggedOut.set(true);
    }
    public String generateOwnBumpString() throws Exception {
        // 1. Generate a new BumpObject
        BumpObject bump = ChatCrypto.init();

        // 2. Get the SecretKey and encode it to Base64
        SecretKey initialKey = bump.getKey();
        String keyString = Base64.getEncoder().encodeToString(initialKey.getEncoded());

        // 3. Create the bump string
        return currentUser + "|" + keyString + "|" + bump.getIdx() + "|" + bump.getTag();
    }



    //  Method to accept a new chat based on the received bump string
    public boolean acceptNewChat(String myBumpString, String otherBumpString) {
        try {
            String[] myParts = myBumpString.split("\\|");
            String[] otherParts = otherBumpString.split("\\|");

            if (myParts.length != 4 || otherParts.length != 4) {
                log.error("Invalid bump string format");
                return false;
            }

            String myName = myParts[0];
            String otherName = otherParts[0];

            // Check if myName matches the logged-in user
            if (!myName.equals(currentUser)) {
                log.error("Bump string name {} does not match logged-in user {}", myName, currentUser);
            }

            // No duplicate chats allowed
            boolean alreadyExists = activeChats.stream()
                    .anyMatch(c -> c.recipient.equals(otherName));
            if (alreadyExists) {
                log.error("Chat with {} already exists", otherName);
                return false;
            }

            SecretKey myKey = ChatCrypto.decodeKey(myParts[1]);
            long myIdx = Long.parseLong(myParts[2]);
            String myTag = myParts[3];

            SecretKey otherKey = ChatCrypto.decodeKey(otherParts[1]);
            long otherIdx = Long.parseLong(otherParts[2]);
            String otherTag = otherParts[3];

            ChatState chat = new ChatState(
                    otherName,
                    myKey, myIdx, myTag,
                    otherKey, otherIdx, otherTag
            );

            activeChats.add(chat);
            return true;

        } catch (Exception e) {
            log.error("Exception while accepting new chat", e);

            return false;
        }
    }
    public String getDebugStateForIndex(int listIndex) {
        // listIndex = index in the ListView
        // 0 = "➕ New Chat (BUMP)"
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) return "";
        return activeChats.get(idx).debugInfo();
    }



    //  Retrieves the names of the chats for the GUI
    public List<String> getChatNames() {
        List<String> names = new ArrayList<>();
        names.add("➕ New Chat (BUMP)");
        activeChats.forEach(chat -> names.add(chat.toString()));
        return names;
    }

    /**
     *
     * @param chatIndex : index in the list (0 = new chat, 1 = first chat, etc)
     * @param message : the message to send
     */
    public void sendMessage(int chatIndex, String message) {
        // 0 = "➕ New Chat (BUMP)", real chats start at 1
        chatIndex -= 1;
        if (chatIndex < 0 || chatIndex >= activeChats.size()) {
            log.error("No valid chat selected for sending message.");
            return;
        }

        ChatState chat = activeChats.get(chatIndex);

        try {
            // 1. generate new idx' and tag' (for the next step in the chain)
            long nextIdx = ChatCrypto.makeNewIdx();
            String nextTag = ChatCrypto.makeNewTag();

            // 2. payload m || nextIdx || nextTag

            String payload = message + "_" + nextIdx + "_" + nextTag;
            log.info("To be encrypted payload: {}", payload);

            // 3. encrypt with the current outKey of this chat
            SecretKey outKey = chat.sendKey;
            String encryptedMessage = ChatCrypto.encrypt(payload, outKey);

            // 4. t = B(b) calculate with current outTag (b)
            String currentTag = chat.sendTag;
            String t = Encryption.preimageToTag(currentTag); // here we hash the tag, the server must do this too

            // 5. send to the server at index = current outIdx
            long currentIdx = chat.sendIdx;
            bulletinBoard.add((int) currentIdx, encryptedMessage, t);

            log.info("Sent encrypted message at idx {} with tag {}", currentIdx, t);

            // 6. update local state to idx', tag'
            chat.sendIdx = nextIdx;
            chat.sendTag = nextTag;
            chat.sendKey = ChatCrypto.makeNewSecretKey(outKey);// update key for forward secrecy

        } catch (Exception e) {
            log.error("Exception while sending message", e);
        }
    }
    public List<String> fetchMessages(int listIndex) throws RemoteException {
        List<String> received = new ArrayList<>();

        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) {
            log.error("No valid chat selected for fetching messages.");
            return received; // empty list
        }

        ChatState chat = activeChats.get(idx);

        while (true) {
            Pair pair = bulletinBoard.get((int) chat.recvIdx, chat.recvTag);
            if (pair == null) {
                // No message found, stop fetching
                break;
            } else {
                String encryptedMessage = pair.value();
                try {
                    String payload = ChatCrypto.decrypt(encryptedMessage, chat.recvKey);

                    String[] parts = payload.split("_", 3);
                    if (parts.length != 3) {
                        log.error("Invalid payload format: {}", payload);
                        break;
                    }

                    String receivedMessage = parts[0];
                    long nextIdx = Long.parseLong(parts[1]);
                    String nextTag = parts[2];

                    log.info("Received message: {}", receivedMessage);
                    received.add(receivedMessage);

                    // advance chain
                    chat.recvIdx = nextIdx;
                    chat.recvTag = nextTag;
                    chat.recvKey = ChatCrypto.makeNewSecretKey(chat.recvKey);

                } catch (Exception e) {
                    log.error("Exception while decrypting message", e);
                    break;
                }
            }
        }

        return received;
    }
    public String getRecipientName(int listIndex) {
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) return "?";
        return activeChats.get(idx).recipient;
    }


}
