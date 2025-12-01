package org.example.cypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

public class KeyStoreImpl {
    private static final Logger log = LoggerFactory.getLogger(KeyStoreImpl.class);
    private KeyStore keyStore;
    private char[] keystorePassword;
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String EXTENSION = ".p12";
    private static final String DB_KEY_ALIAS = "database-encryption-key";

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

            // Generate database encryption key (DEK)
            SecretKey dbKey = generateDatabaseKey();
            KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(password.toCharArray());
            KeyStore.SecretKeyEntry skEntry = new KeyStore.SecretKeyEntry(dbKey);
            ks.setEntry(DB_KEY_ALIAS, skEntry, protParam);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                ks.store(fos, password.toCharArray());
            } catch (Exception e) {
                log.error("Exception while saving keystore for user {}", username, e);
                return false;
            }

            this.keyStore = ks;
            this.keystorePassword = password.toCharArray();
            log.info("Created keystore with database encryption key for user {}", username);
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
            this.keystorePassword = password.toCharArray();
            return true;
        } catch (Exception e) {
            log.error("Exception while loading keystore for user {}", username, e);
            return false;
        }
    }


    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * Retrieves the database encryption key from the keystore.
     * @return Database encryption key (DEK) or null if not found
     */
    public SecretKey getDatabaseKey() {
        if (keyStore == null || keystorePassword == null) {
            log.error("Keystore not loaded");
            return null;
        }

        try {
            KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(keystorePassword);
            KeyStore.Entry entry = keyStore.getEntry(DB_KEY_ALIAS, protParam);

            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }

            log.error("Database key not found in keystore");
            return null;
        } catch (Exception e) {
            log.error("Failed to retrieve database key", e);
            return null;
        }
    }

    /**
     * Generates a new AES-256 key for database encryption.
     */
    private SecretKey generateDatabaseKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    private File getKeystoreFile(String username) throws IOException {
        // zorg dat de map bestaat
        Files.createDirectories(BASE_DIR);
        Path ksPath = BASE_DIR.resolve(username + EXTENSION);
        log.info("Keystore path for user {}: {}", username, ksPath.toAbsolutePath());
        return ksPath.toFile();
    }
}
