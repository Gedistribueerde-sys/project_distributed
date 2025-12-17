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


        long nonce = random.nextLong(); // Genereert een willekeurige startwaarde voor de nonce om gelijktijdige botsingen te vermijden
        byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8); // Zet de tag (String) om naar bytes in UTF-8 zodat deze kan worden gehasht
        ByteBuffer idxBuffer = ByteBuffer.allocate(Long.BYTES); // Maakt een ByteBuffer van 8 bytes om de long-waarde idx op te slaan
        idxBuffer.putLong(idx); // Schrijft de waarde van idx in de buffer
        byte[] idxBytes = idxBuffer.array(); // Haalt de byte-array uit de buffer zodat idx in binaire vorm gebruikt kan worden


        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // Maak een SHA-256 hash-object aan om data te hashen

            while (true) { // Oneindige lus: blijf nonces proberen tot een geldige gevonden wordt

                digest.reset(); // Reset de interne toestand van de hashfunctie voor een nieuwe berekening
                digest.update(tagBytes); // Voeg de bytes van de tag toe aan de hash-input
                digest.update(idxBytes); // Voeg de bytes van de index (idx) toe aan de hash-input
                ByteBuffer nonceBuffer = ByteBuffer.allocate(Long.BYTES); // Maak een ByteBuffer van 8 bytes voor de nonce
                nonceBuffer.putLong(nonce); // Zet de huidige nonce-waarde om naar bytes
                digest.update(nonceBuffer.array()); // Voeg de nonce-bytes toe aan de hash-input (tag || idx || nonce)
                byte[] hash = digest.digest(); // Bereken de SHA-256 hash (resultaat is 32 bytes)

                if (hasLeadingZeros(hash, difficultyBits)) { // Controleer of de hash voldoet aan de moeilijkheid (leading zero bits)
                    long endTime = System.currentTimeMillis(); // Registreer het tijdstip waarop een geldige nonce is gevonden
                    return new ProofResult(nonce, endTime - startTime); // Geef de nonce en de berekende tijd terug
                }

                nonce++; // Verhoog de nonce en probeer opnieuw
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // Maak een SHA-256 hash-object aan om data te hashen

            byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8); // Zet de tag (String) om naar UTF-8 bytes zodat deze kan worden gehasht

            ByteBuffer idxBuffer = ByteBuffer.allocate(Long.BYTES); // Maakt een ByteBuffer van 8 bytes om de long-waarde idx op te slaan
            idxBuffer.putLong(idx); // Schrijft de waarde van idx in de buffer
            byte[] idxBytes = idxBuffer.array(); // Haalt de byte-array uit de buffer zodat idx in binaire vorm gebruikt kan worden

            ByteBuffer nonceBuffer = ByteBuffer.allocate(Long.BYTES); // Maakt een ByteBuffer van 8 bytes om de nonce (long) op te slaan
            nonceBuffer.putLong(nonce); // Schrijft de huidige nonce-waarde in de buffer
            byte[] nonceBytes = nonceBuffer.array(); // Haalt de byte-array uit de buffer zodat de nonce kan worden gehasht

            digest.update(tagBytes); // Voeg de tag-bytes toe aan de hash-input
            digest.update(idxBytes); // Voeg de index-bytes toe aan de hash-input
            digest.update(nonceBytes); // Voeg de nonce-bytes toe aan de hash-input (tag || idx || nonce)

            byte[] hash = digest.digest(); // Bereken de SHA-256 hash van alle toegevoegde bytes (resultaat is 32 bytes)


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
            // Loop door alle bytes die volledig nul moeten zijn
            if (hash[i] != 0) {
                // Als een van deze bytes niet nul is, voldoet de hash niet
                return false;
            }
        }
        // Check remaining bits in the next byte
        if (remainingBits > 0 && fullBytes < hash.length) {
            // Controleer of er nog bits gecontroleerd moeten worden en of de byte bestaat
            int mask = 0xFF << (8 - remainingBits);
            // Maak een bitmasker waarbij de eerste 'remainingBits' bits op 1 staan
            return (hash[fullBytes] & mask) == 0;
            // Controleer of de vereiste leidende bits in de volgende byte nul zijn
        }

        return true;
    }
}

