package org.example;

import java.util.ArrayList;
import java.util.List;

public class Controller {

    private final List<String> messages = new ArrayList<>();
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

    public void sendMessage(String text) {
        if (currentUser == null || text == null || text.isBlank()) return;
        messages.add(currentUser + ": " + text);
    }

    public List<String> getMessages() {
        return new ArrayList<>(messages);
    }
}
