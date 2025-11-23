package org.example;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

public class ChatCrypto {
    static final private SecureRandom secureRandom = new SecureRandom();
    public static BumpObject init() throws Exception {
        // random number genrator to do start crypto operations


        // Genereert een veilig, uniform getal tussen 0 long max value
        long rawId = secureRandom.nextLong();

        // Zorg dat het positief is (Math.abs kan falen bij Long.MIN_VALUE, dus bitmask is veiliger)
        //  1xxxxxxx  (Het willekeurige getal, start met 1 dus NEGATIEF)
        //& 01111111  (Long.MAX_VALUE, start met 0 dus POSITIEF)
        //  --------
        //  0xxxxxxx  (Het resultaat)

        long idx = rawId & Long.MAX_VALUE;
        System.out.println("Generated Chat ID: " + idx);


        //genereren van de tag : 32 bytes = 256 bits
        byte[] tagBytes = new byte[32];
        secureRandom.nextBytes(tagBytes);
        // encodeer eht naar een string
        String initialTag = Base64.getEncoder().encodeToString(tagBytes);

        //genereren van een initiele key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        // Init een sleuetel die 256 groot is
        keyGen.init(256, secureRandom);
        SecretKey secretKey = keyGen.generateKey();


        return new BumpObject( idx, initialTag,secretKey);
    }
    // Decodeer de Base64 key string terug naar een SecretKey object
    public static SecretKey decodeKey(String keyString) {
        byte[] decodedKey = Base64.getDecoder().decode(keyString);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }
    public static long makeNewIdx() {
        return  secureRandom.nextLong() & Long.MAX_VALUE;
    }
    public static String makeNewTag() {
        byte[] tagBytes = new byte[32];
        secureRandom.nextBytes(tagBytes);
        return Base64.getEncoder().encodeToString(tagBytes);
    }
    // gpt stelt GCM-mode, IV's of best practices voor
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
    // update de private key naar een nieuwe private key
    // gelijk voor bijde clients
    public static SecretKey makeNewSecretKey(SecretKey oldKey) throws NoSuchAlgorithmException {
        byte[] oldBytes = oldKey.getEncoded();

        // Hash de key met SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(oldBytes);

        // Gebruik de eerste 32 bytes als nieuwe AES-256 key
        return new SecretKeySpec(hash, 0, 32, "AES");

    }

}
