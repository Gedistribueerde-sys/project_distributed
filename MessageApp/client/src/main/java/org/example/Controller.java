package org.example;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Controller {

    private final List<ChatState> activeChats = new ArrayList<>();
    private final KeyStoreImpl keyStore = new KeyStoreImpl();
    private String currentUser;

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
}
