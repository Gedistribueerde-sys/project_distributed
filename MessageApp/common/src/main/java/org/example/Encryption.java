package org.example;
import java.nio.charset.*;
import java.security.*;
public class Encryption {
    /**
     * B(b): hashfunctie van een String b naar een hex-string t.
     * Hier gebruiken we SHA-256.
     */
    public static String B(String b) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // digest object
            byte[] hash = digest.digest(b.getBytes(StandardCharsets.UTF_8)); // digest object gebruiken om hash te maken

            // bytes -> hex
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte h : hash) {
                sb.append(String.format("%02x", h));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // In een normale JVM is SHA-256 altijd aanwezig
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
