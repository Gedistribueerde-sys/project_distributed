package org.example;


import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;

public class KeyStoreImpl {
    private KeyStore keyStore;
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String EXTENSION = ".p12";

    private static final Path BASE_DIR = Paths.get("MessageApp", "client");

    /**
     * this function needs to create a keystore for the user with the given username and password
     * the keystore name = username + ".jks" or another extension
     * the keystore needs to be encrypted with the given password
     * return true if the keystore was created successfully, false otherwise
     */
    public boolean makeKeyStore(String username, String password) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            return false;
        }

        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            ks.load(null, password.toCharArray());

            File file = getKeystoreFile(username);

            // als je NIET wil overschrijven:
            if (file.exists()) {
                System.out.println("Keystore bestaat al voor gebruiker: " + username);
                return false;
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                ks.store(fos, password.toCharArray());
            }

            this.keyStore = ks;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * loads the private keystore into memory if the username and password are correct
     * return true if the keystore was loaded successfully, false otherwise
     */
    public boolean loadKeyStore(String username, String password) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            return false;
        }
        try {
            File file = getKeystoreFile(username);
            if (!file.exists()) {
                return false;
            }

            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            try (FileInputStream fis = new FileInputStream(file)) {
                ks.load(fis, password.toCharArray());
            }

            this.keyStore = ks;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public KeyStore getKeyStore() {
        return keyStore;
    }
    private File getKeystoreFile(String username) throws IOException {
        // zorg dat de map bestaat
        Files.createDirectories(BASE_DIR);
        Path ksPath = BASE_DIR.resolve(username + EXTENSION);
        System.out.println("Keystore path: " + ksPath.toAbsolutePath());
        return ksPath.toFile();
    }
}
