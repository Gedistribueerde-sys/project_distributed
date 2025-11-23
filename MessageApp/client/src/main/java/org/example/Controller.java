package org.example;

import javax.crypto.SecretKey;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Controller {


    private final List<ChatState> activeChats = new ArrayList<>();
    private final KeyStoreImpl keyStore = new KeyStoreImpl();
    private String currentUser;
    private BulletinBoard bulletinBoard;

    public Controller(BulletinBoard bulletinBoard) {this.bulletinBoard = bulletinBoard;}

    public boolean register(String username, String password) {
        // gewoon proberen
        boolean created = keyStore.makeKeyStore(username, password);
        if (created) {
            System.out.println("Keystore aangemaakt voor gebruiker: " + username);
        } else {
            System.out.println("Registratie mislukt (keystore kon niet worden gemaakt)");
        }
        return created;
    }

    public boolean login(String username, String password) {
        boolean loaded = keyStore.loadKeyStore(username, password);
        if (loaded) {
            currentUser = username;
            System.out.println("Login succesvol als: " + username);
        } else {
            System.out.println("Login mislukt: fout wachtwoord of gebruiker bestaat niet.");
        }
        return loaded;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void logout() {
        currentUser = null;
        System.out.println("Uitgelogd.");
    }
    public String generateOwnBumpString() throws Exception {
        // 1. Genereer de cryptografische elementen
        BumpObject bump = ChatCrypto.init();

        // 2. Haal de key op en codeer die naar Base64
        SecretKey initialKey = bump.getKey();
        String keyString = Base64.getEncoder().encodeToString(initialKey.getEncoded());

        // 3. Maak de string: NAAM|KEY|IDX|TAG
        return currentUser + "|" + keyString + "|" + bump.getIdx() + "|" + bump.getTag();
    }



    //  Methode om de bump-string te accepteren
    public boolean acceptNewChat(String myBumpString, String otherBumpString) {
        try {
            String[] myParts = myBumpString.split("\\|");
            String[] otherParts = otherBumpString.split("\\|");

            if (myParts.length != 4 || otherParts.length != 4) {
                System.err.println("Ongeldig bump-formaat");
                return false;
            }

            String myName = myParts[0];
            String otherName = otherParts[0];

            // Optioneel: check of myName gelijk is aan currentUser
            if (!myName.equals(currentUser)) {
                System.err.println("Waarschuwing: naam in eigen bumpstring ≠ ingelogde user");
            }

            // Geen dubbele chat met dezelfde persoon
            boolean alreadyExists = activeChats.stream()
                    .anyMatch(c -> c.recipient.equals(otherName));
            if (alreadyExists) {
                System.err.println("Chat met " + otherName + " bestaat al");
                return false;
            }

            // JOUW richting (jij -> ander) komt uit je eigen bumpstring
            SecretKey myKey = ChatCrypto.decodeKey(myParts[1]);
            long myIdx = Long.parseLong(myParts[2]);
            String myTag = myParts[3];

            // HUN richting (ander -> jij) komt uit hun bumpstring
            SecretKey otherKey = ChatCrypto.decodeKey(otherParts[1]);
            long otherIdx = Long.parseLong(otherParts[2]);
            String otherTag = otherParts[3];

            ChatState chat = new ChatState(
                    otherName,
                    myKey, myIdx, myTag,         // OUT: jij -> ander
                    otherKey, otherIdx, otherTag // IN: ander -> jij
            );

            activeChats.add(chat);
            return true;

        } catch (Exception e) {
            System.err.println("Fout bij het accepteren van de bump string: " + e.getMessage());
            return false;
        }
    }
    public String getDebugStateForIndex(int listIndex) {
        // listIndex = index in de ListView
        // 0 = "➕ Nieuwe chat (BUMP)"
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) return "";
        return activeChats.get(idx).debugInfo();
    }



    //  Haalt de namen van de chats op voor de GUI
    public List<String> getChatNames() {
        List<String> names = new ArrayList<>();
        names.add("➕ Nieuwe chat (BUMP)");
        activeChats.forEach(chat -> names.add(chat.toString()));
        return names;
    }

    /**
     *
     * @param listIndex : index in de lijst (0 = nieuwe chat, 1 = eerste chat, etc)
     * @param message : het bericht om te versturen
     */
    public void sendMessage(int listIndex, String message) {
        // 0 = "➕ Nieuwe chat (BUMP)", echte chats beginnen op 1
        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) {
            System.err.println("Geen geldige chat geselecteerd.");
            return;
        }

        ChatState chat = activeChats.get(idx);

        try {
            // 1. nieuwe idx' en tag' genereren (voor de volgende stap in de ketting)
            long nextIdx = ChatCrypto.makeNewIdx();
            String nextTag = ChatCrypto.makeNewTag();

            // 2. payload m || nextIdx || nextTag
            StringBuilder sb = new StringBuilder();
            sb.append(message);
            sb.append("_");
            sb.append(nextIdx);
            sb.append("_");
            sb.append(nextTag);

            String payload = sb.toString();
            System.out.println("Te versleutelen payload: " + payload);

            // 3. encrypt met de huidige outKey van deze chat
            SecretKey outKey = chat.sendKey;
            String encryptedMessage = ChatCrypto.encrypt(payload, outKey);

            // 4. t = B(b) berekenen met huidige outTag (b)
            String currentTag = chat.sendTag;
            String t = Encryption.B(currentTag); // hier hashen we de tag , server moet dit ook doen

            // 5. versturen naar de server op index = current outIdx
            long currentIdx = chat.sendIdx;
            bulletinBoard.add((int) currentIdx, encryptedMessage, t);

            System.out.println("Versleuteld bericht gepost op idx " +
                    currentIdx + " met t = B(tag).");

            // 6. lokale state updaten naar idx', tag'
            chat.sendIdx = nextIdx;
            chat.sendTag =nextTag;
            chat.sendKey = ChatCrypto.makeNewSecretKey(outKey);// key updaten voor forward secrecy

        } catch (Exception e) {
            System.err.println("Fout bij versturen: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public List<String> fetchMessages(int listIndex) throws RemoteException {
        List<String> received = new ArrayList<>();

        int idx = listIndex - 1;
        if (idx < 0 || idx >= activeChats.size()) {
            System.err.println("Geen geldige chat geselecteerd.");
            return received; // lege lijst
        }

        ChatState chat = activeChats.get(idx);

        while (true) {
            Pair pair = bulletinBoard.get((int) chat.recvIdx, chat.recvTag);
            if (pair == null) {
                // Geen bericht gevonden, stoppen met ophalen
                break;
            } else {
                String encryptedMessage = pair.getV();
                try {
                    String payload = ChatCrypto.decrypt(encryptedMessage, chat.recvKey);

                    String[] parts = payload.split("_", 3);
                    if (parts.length != 3) {
                        System.err.println("Ongeldig payload-formaat: " + payload);
                        break;
                    }

                    String receivedMessage = parts[0];
                    long nextIdx = Long.parseLong(parts[1]);
                    String nextTag = parts[2];

                    System.out.println("Ontvangen bericht: " + receivedMessage);
                    received.add(receivedMessage);

                    // ketting vooruit
                    chat.recvIdx = nextIdx;
                    chat.recvTag = nextTag;
                    chat.recvKey = ChatCrypto.makeNewSecretKey(chat.recvKey);

                } catch (Exception e) {
                    System.err.println("Fout bij decrypten: " + e.getMessage());
                    e.printStackTrace();
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
