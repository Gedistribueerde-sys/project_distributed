package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

public class ChatCrypto {
    private static final Logger log = LoggerFactory.getLogger(ChatCrypto.class);
    static final private SecureRandom secureRandom = new SecureRandom();

    public static BumpObject init() throws Exception {

        // generates a random long for the chat idx
        long randomNum = secureRandom.nextLong();

        long idx = randomNum & Long.MAX_VALUE;
        log.info("Generated Chat ID: {}", idx);

        // generate tag: 32 bytes = 256 bits
        byte[] tagBytes = new byte[32];
        secureRandom.nextBytes(tagBytes);
        // encode to Base64 string
        String initialTag = Base64.getEncoder().encodeToString(tagBytes);

        // generate AES-256 key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, secureRandom);
        SecretKey secretKey = keyGen.generateKey();

        return new BumpObject( idx, initialTag,secretKey);
    }


    // Decode a Base64-encoded string back into a SecretKey
    public static SecretKey decodeKey(String keyString) {
        byte[] decodedKey = Base64.getDecoder().decode(keyString);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    public static String keyToBase64(SecretKey key) {
        if (key == null || key.getEncoded() == null) return "-";
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static long makeNewIdx() {
        return  secureRandom.nextLong() & Long.MAX_VALUE; // zorg dat het positief is
    }


    public static byte[] makeNewTag() {
        byte[] tagBytes = new byte[32];
        secureRandom.nextBytes(tagBytes);
        return tagBytes;
    }
    
    public static String tagToBase64(byte[] tagBytes) {
        return Base64.getEncoder().encodeToString(tagBytes);
    }

    public static byte[] encryptPayloadBytes(byte[] payload, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // Generate random IV (12 bytes is recommended for GCM)
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);

        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv); // 128-bit authentication tag
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        byte[] ciphertext = cipher.doFinal(payload);

        // Prepend IV to ciphertext (needed for decryption)
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

        return result;
    }

    public static byte[] decryptPayloadBytes(byte[] encryptedPayload, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // Extract IV from the beginning of the encrypted payload
        byte[] iv = new byte[12];
        System.arraycopy(encryptedPayload, 0, iv, 0, iv.length);

        // Extract the actual ciphertext
        byte[] ciphertext = new byte[encryptedPayload.length - iv.length];
        System.arraycopy(encryptedPayload, iv.length, ciphertext, 0, ciphertext.length);

        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        return cipher.doFinal(ciphertext);
    }

    // Derive a new SecretKey from an old one using SHA-256
    public static SecretKey makeNewSecretKey(SecretKey oldKey) throws NoSuchAlgorithmException {
        byte[] oldBytes = oldKey.getEncoded();

        // Hash the old key bytes using SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(oldBytes);

        // Use the hash as the new key
        return new SecretKeySpec(hash, 0, 32, "AES");

    }
}
