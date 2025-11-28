package org.example;
import java.nio.charset.*;
import java.security.*;
public class Encryption {
    /**
     * B(b): hash function from a String b to a hex-string t.
     * We use SHA-256 here.
     */
    public static String preimageToTag(String preimage) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // digest object
            byte[] hash = digest.digest(preimage.getBytes(StandardCharsets.UTF_8)); // use digest object to create hash

            // bytes -> hex
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte h : hash) {
                sb.append(String.format("%02x", h));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // In a normal JVM, SHA-256 is always present
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
