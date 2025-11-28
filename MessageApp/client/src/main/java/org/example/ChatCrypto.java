package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
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


    public static long makeNewIdx() {
        return  secureRandom.nextLong() & Long.MAX_VALUE; // zorg dat het positief is
    }


    public static String makeNewTag() {
        byte[] tagBytes = new byte[32];
        secureRandom.nextBytes(tagBytes);
        return Base64.getEncoder().encodeToString(tagBytes);
    }


    public static String encrypt(String message, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(message.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }


    public static String decrypt(String base64Cipher, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decoded = Base64.getDecoder().decode(base64Cipher);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, "UTF-8");
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
