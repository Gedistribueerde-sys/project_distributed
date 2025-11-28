package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;

public class KeyStoreImpl {
    private static final Logger log = LoggerFactory.getLogger(KeyStoreImpl.class);
    private KeyStore keyStore;
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String EXTENSION = ".p12";

    private static final Path BASE_DIR = Paths.get("MessageApp", "client");

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
                log.info("Keystore already exists for user {}", username);
                return false;
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                ks.store(fos, password.toCharArray());
            } catch (Exception e) {
                log.error("Exception while saving keystore for user {}", username, e);
                return false;
            }

            this.keyStore = ks;
            return true;
        } catch (Exception e) {
            log.error("Exception while creating keystore for user {}", username, e);
            return false;
        }
    }

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
            log.error("Exception while loading keystore for user {}", username, e);
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
        log.info("Keystore path for user {}: {}", username, ksPath.toAbsolutePath());
        return ksPath.toFile();
    }
}
