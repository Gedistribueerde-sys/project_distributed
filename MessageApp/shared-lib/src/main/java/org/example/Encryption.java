package org.example;
import java.nio.charset.*;
import java.security.*;
import java.util.Base64;

public class Encryption {
    /**
     * preimageToTag(preimage): hash function from a String preimage to a base64 tag.
     * We use SHA-256 here.
     */
    public static String preimageToTag(String preimage) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // digest object
            byte[] hash = digest.digest(preimage.getBytes(StandardCharsets.UTF_8)); // use digest object to create hash

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // In a normal JVM, SHA-256 is always present
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
