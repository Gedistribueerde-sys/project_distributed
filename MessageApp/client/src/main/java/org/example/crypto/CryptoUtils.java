package org.example.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

// Utility class for AES-GCM encryption and decryption with AAD support
public final class CryptoUtils {
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // bytes
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Encrypts data producing IV || ciphertext format.
    public static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] aad) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        if (aad != null) {
            cipher.updateAAD(aad);
        }
        byte[] ct = cipher.doFinal(plaintext);

        return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
    }

    // Decrypts data in IV || ciphertext format.
    public static byte[] decrypt(byte[] encrypted, SecretKey key, byte[] aad) throws GeneralSecurityException {
        if (encrypted == null || encrypted.length < IV_LENGTH) {
            throw new GeneralSecurityException("Invalid encrypted payload");
        }
        ByteBuffer bb = ByteBuffer.wrap(encrypted);
        byte[] iv = new byte[IV_LENGTH];
        bb.get(iv);
        byte[] ct = new byte[bb.remaining()];
        bb.get(ct);

        Cipher cipher = Cipher.getInstance(ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        if (aad != null) {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(ct);
    }

    // Create AAD (additional authentication data) from username and recipient ID
    public static byte[] makeAAD(String username, String recipientId) {
        return (username + ":" + recipientId).getBytes(StandardCharsets.UTF_8);
    }
}

