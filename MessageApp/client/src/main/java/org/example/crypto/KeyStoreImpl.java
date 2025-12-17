package org.example.crypto;

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
    // PKCS12 is de moderne, platformonafhankelijke standaard voor keystores (vervangt het oude JKS).
    // Het ondersteunt sterke encryptie en wordt herkend door bijna alle tools en programmeertalen.
    private static final String KEYSTORE_TYPE = "PKCS12";

    // De .p12 extensie (of soms .pfx) is de universele bestandsextensie voor PKCS12-bestanden.
    // Hiermee is het bestand direct herkenbaar voor zowel het besturingssysteem als beveiligingssoftware.
    private static final String EXTENSION = ".p12";
    private static final String DB_KEY_ALIAS = "database-encryption-key";

    private static final Path BASE_DIR = Paths.get("MessageApp", "client", "data");

    public boolean makeKeyStore(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }

        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            ks.load(null, password.toCharArray());

            File file = getKeystoreFile(username);

            // Check if keystore already exists, do not overwrite
            if (file.exists()) {
                log.info("Keystore already exists for user {}", username);
                return false;
            }

            // Generate database encryption key (DEK)
            SecretKey dbKey = generateDatabaseKey();
            //Een ProtectionParameter bepaalt hoe een entry in een KeyStore wordt beveiligd.
            KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(password.toCharArray()); // Maakt een bescherming aan voor de keystore-entry met het opgegeven wachtwoord
            KeyStore.SecretKeyEntry skEntry = new KeyStore.SecretKeyEntry(dbKey); // Verpakt de geheime sleutel (dbKey) in een SecretKeyEntry voor opslag in de keystore
            ks.setEntry(DB_KEY_ALIAS, skEntry, protParam); // Slaat de geheime sleutel op in de keystore onder de opgegeven alias, beveiligd met het wachtwoord


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

    // Loads the keystore for the given username and password.
    public boolean loadKeyStore(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        try {
            File file = getKeystoreFile(username);
            if (!file.exists()) {
                return false;
            }

            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            try (FileInputStream fis = new FileInputStream(file)) { // Opent een FileInputStream naar het keystore-bestand en sluit deze automatisch na gebruik
                ks.load(fis, password.toCharArray()); // Laadt de keystore-inhoud uit het bestand en ontgrendelt deze met het opgegeven wachtwoord
            }

            this.keyStore = ks;
            this.keystorePassword = password.toCharArray();
            return true;
        } catch (Exception e) {
            log.error("Exception while loading keystore for user {}", username, e);
            return false;
        }
    }


    // Retrieves the database encryption key from the loaded keystore.
    public SecretKey getDatabaseKey() {
        if (keyStore == null || keystorePassword == null) {
            log.error("Keystore not loaded");
            return null;
        }

        try {
            KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(keystorePassword); // Maakt een ProtectionParameter aan die aangeeft dat de keystore-entry beschermd is met een wachtwoord
            // je moet zeggen hoe het beschermd is om het eruit te halen

            KeyStore.Entry entry = keyStore.getEntry(DB_KEY_ALIAS, protParam); // Haalt de entry uit de keystore op met de opgegeven alias en het bijhorende wachtwoord

            if (entry instanceof KeyStore.SecretKeyEntry) { // Controleert of de opgehaalde entry effectief een SecretKeyEntry is
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey(); // Cast de entry naar SecretKeyEntry en haalt de geheime sleutel eruit
            }


            log.error("Database key not found in keystore");
            return null;
        } catch (Exception e) {
            log.error("Failed to retrieve database key", e);
            return null;
        }
    }

    // Generates a new AES database encryption key.
    private SecretKey generateDatabaseKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES"); // Maakt een KeyGenerator aan voor het AES-algoritme om een symmetrische sleutel te genereren
        keyGen.init(256);
        return keyGen.generateKey();// Genereert en geeft een nieuwe willekeurige AES SecretKey terug
    }

    // Gets the keystore file path for the given username.
    private File getKeystoreFile(String username) throws IOException {
        Files.createDirectories(BASE_DIR);
        Path ksPath = BASE_DIR.resolve(username + EXTENSION);
        log.info("Keystore path for user {}: {}", username, ksPath.toAbsolutePath());
        return ksPath.toFile();
    }
}
