package org.example;
import java.nio.charset.*;
import java.security.*;
import java.util.Base64;

public class Encryption {

     // preimageToTag(preimage): hash function from a String preimage to a base64 tag.
     //We use SHA-256 here.
    public static String preimageToTag(String preimage) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // digest object
            byte[] hash = digest.digest(preimage.getBytes(StandardCharsets.UTF_8));
            // Zet de preimage (String) om naar UTF-8 bytes en bereken er in één stap de SHA-256 hash van

            return Base64.getEncoder().encodeToString(hash);
            // Encodeer de binaire hash naar een Base64-String zodat ze leesbaar en makkelijk opslaan/verzenden is
        } catch (NoSuchAlgorithmException e) {
            // In a normal JVM, SHA-256 is always present
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
