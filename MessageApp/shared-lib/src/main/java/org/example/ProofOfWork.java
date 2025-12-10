package org.example;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class ProofOfWork {

    /*
     Number of leading zero bits required in the hash.
     Difficulty setting controls computational cost:
     - 16 bits: ~65K attempts, ~5-10ms
     - 18 bits: ~262K attempts, ~20-40ms
     - 20 bits: ~1M attempts, ~80-150ms
     - 22 bits: ~4M attempts, ~200-400ms
     - 23 bits: ~8M attempts, ~400-800ms
     - 24 bits: ~16M attempts, ~1-2s
     Tested on i7-11800H, 22 bits provides a good
     balance between DDoS protection (~244ms computation) and user experience.
     Since a i7-11800H is fairly powerful we choose for 22 bits.
     */
    public static final int DIFFICULTY_BITS = 22;

    private static final SecureRandom random = new SecureRandom();

    public record ProofResult(long nonce, long computationTimeMs) {}

    public static ProofResult computeProof(String tag, long idx) {
        return computeProof(tag, idx, DIFFICULTY_BITS);
    }

    public static ProofResult computeProof(String tag, long idx, int difficultyBits) {
        long startTime = System.currentTimeMillis();

        // Start with a random nonce to avoid collisions when multiple clients compute simultaneously
        long nonce = random.nextLong();

        byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);
        ByteBuffer idxBuffer = ByteBuffer.allocate(Long.BYTES);
        idxBuffer.putLong(idx);
        byte[] idxBytes = idxBuffer.array();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            while (true) {
                digest.reset();
                digest.update(tagBytes);
                digest.update(idxBytes);

                ByteBuffer nonceBuffer = ByteBuffer.allocate(Long.BYTES);
                nonceBuffer.putLong(nonce);
                digest.update(nonceBuffer.array());

                byte[] hash = digest.digest();

                if (hasLeadingZeros(hash, difficultyBits)) {
                    long endTime = System.currentTimeMillis();
                    return new ProofResult(nonce, endTime - startTime);
                }

                nonce++;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static boolean verifyProof(String tag, long idx, long nonce) {
        return verifyProof(tag, idx, nonce, DIFFICULTY_BITS);
    }

    public static boolean verifyProof(String tag, long idx, long nonce, int difficultyBits) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);
            ByteBuffer idxBuffer = ByteBuffer.allocate(Long.BYTES);
            idxBuffer.putLong(idx);
            byte[] idxBytes = idxBuffer.array();

            ByteBuffer nonceBuffer = ByteBuffer.allocate(Long.BYTES);
            nonceBuffer.putLong(nonce);
            byte[] nonceBytes = nonceBuffer.array();

            digest.update(tagBytes);
            digest.update(idxBytes);
            digest.update(nonceBytes);

            byte[] hash = digest.digest();

            return hasLeadingZeros(hash, difficultyBits);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static boolean hasLeadingZeros(byte[] hash, int numBits) {
        int fullBytes = numBits / 8;
        int remainingBits = numBits % 8;

        // Check full bytes (must all be zero)
        for (int i = 0; i < fullBytes; i++) {
            if (hash[i] != 0) {
                return false;
            }
        }

        // Check remaining bits in the next byte
        if (remainingBits > 0 && fullBytes < hash.length) {
            int mask = 0xFF << (8 - remainingBits);
            return (hash[fullBytes] & mask) == 0;
        }

        return true;
    }
}

