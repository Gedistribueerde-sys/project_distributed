package org.example.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
//De IV (Initialization Vector), ook wel nonce genoemd, zorgt ervoor dat dezelfde tekst die twee keer met dezelfde sleutel wordt versleuteld, toch een totaal andere resultaat (ciphertext) geeft.
// Utility class for AES-GCM encryption and decryption with AAD support
public final class CryptoUtils {
    private static final String ALGO = "AES/GCM/NoPadding";
    // AES = Advanced Encryption Standard, een sterke symmetrische encryptie die vandaag de standaard is
    // GCM = Galois/Counter Mode, een AEAD-modus die zowel encryptie als authenticatie biedt
    //     → Encryptie: verbergt de inhoud van de data
    //     → Authenticatie: detecteert of de data werd aangepast (integriteit + authenticiteit)
    //     → In tegenstelling tot oudere modi (bv. CBC) is geen aparte MAC nodig
    // NoPadding = GCM werkt als een stream cipher (via counter mode)
    //     → Data wordt per byte verwerkt, blokken hoeven niet exact 16 bytes te zijn
    //     → Padding is dus technisch niet nodig
    //     → Padding kan zelfs onveilig zijn (bv. padding oracle attacks bij CBC)
    //     → Door NoPadding te gebruiken vermijd je extra aanvalsvectoren en fouten
    // Samengevat: AES/GCM/NoPadding is modern, veilig, efficiënt en volgt best practices
    private static final int IV_LENGTH = 12;
    // Lengte van de Initialization Vector (nonce) in bytes
    // 12 bytes (96 bits) is de officieel aanbevolen en veiligste lengte voor AES-GCM
    // Dit zorgt voor optimale veiligheid én prestaties



    private static final int TAG_LENGTH_BITS = 128;
    // Lengte van de authenticatietag in bits
    // 128 bits (16 bytes) is de standaard en sterkste GCM-tag
    // Deze tag zorgt ervoor dat manipulatie van de ciphertext wordt gedetecteerd
    private static final SecureRandom RANDOM = new SecureRandom();

    // Encrypts data producing IV || ciphertext format.
    public static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] aad) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH]; // Maak een byte-array aan voor de IV/nonce met lengte IV_LENGTH (bv. 12 bytes bij GCM)
        RANDOM.nextBytes(iv); // Vul de IV met cryptografisch willekeurige bytes (IV moet uniek/onvoorspelbaar zijn per encryptie)

        Cipher cipher = Cipher.getInstance(ALGO); // Maak een Cipher-object aan met het gekozen algoritme (bv. "AES/GCM/NoPadding")
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv); // Maak GCM-parameters: taglengte (bv. 128 bits) + de IV die GCM nodig heeft
        cipher.init(Cipher.ENCRYPT_MODE, key, spec); // Initialiseer de cipher in ENCRYPT-modus met de AES-sleutel en de GCM-parameters (IV + taglengte)

        if (aad != null) { // Check of er AAD (Additional Authenticated Data) is meegegeven
            cipher.updateAAD(aad); // Voeg AAD toe: dit wordt NIET versleuteld maar wel mee-geauthenticeerd (als AAD verandert faalt decryptie)
        }
        byte[] ct = cipher.doFinal(plaintext); // Versleutel de plaintext en maak ook de GCM authenticatietag aan; ct bevat ciphertext + tag achteraan
        return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
        // Plak IV en ciphertext samen in één byte-array: [IV || ciphertext+tag]
        // Dit is handig zodat je bij decryptie de IV meteen terug hebt (de IV is niet geheim, maar moet wel mee opgeslagen/verzonden worden)

    }

    // Decrypts data in IV || ciphertext format.
    public static byte[] decrypt(byte[] encrypted, SecretKey key, byte[] aad) throws GeneralSecurityException {
        if (encrypted == null || encrypted.length < IV_LENGTH) {
            // Controleer of de versleutelde payload bestaat en minstens groot genoeg is om een IV te bevatten
            throw new GeneralSecurityException("Invalid encrypted payload");
            // Gooi een security-exception als de data ongeldig of duidelijk corrupt is
        }

        ByteBuffer bb = ByteBuffer.wrap(encrypted);
        // Wrap de volledige encrypted byte-array in een ByteBuffer om er gestructureerd uit te lezen
        byte[] iv = new byte[IV_LENGTH];
        // Maak een byte-array aan om de IV (nonce) uit de payload te halen
        bb.get(iv);
        // Lees de eerste IV_LENGTH bytes uit de payload en zet ze in de iv-array
        byte[] ct = new byte[bb.remaining()];
        // Maak een byte-array aan voor de resterende bytes (ciphertext + GCM-tag)

        bb.get(ct);
        // Lees de resterende bytes uit de payload (dit is de eigenlijke versleutelde data)

        Cipher cipher = Cipher.getInstance(ALGO);
        // Maak een Cipher-object aan met hetzelfde algoritme als bij encryptie (AES/GCM/NoPadding)

        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        // Maak de GCM-parameters opnieuw aan met dezelfde taglengte en de uitgelezen IV

        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        // Initialiseer de cipher in DECRYPT-modus met de juiste sleutel en IV
        // Als sleutel of IV fout is, zal decryptie later falen

        if (aad != null) {
        // Controleer of er Additional Authenticated Data werd gebruikt bij encryptie
            cipher.updateAAD(aad);
            // Voeg exact dezelfde AAD toe als bij encryptie
            // Als AAD niet overeenkomt, zal decryptie falen (authenticatie mislukt)
        }

        return cipher.doFinal(ct);
        // Probeer de ciphertext te decrypten
        // → Als de data werd aangepast of de key/IV/AAD fout is: exception
        // → Als alles klopt: retourneert de originele plaintext

    }

    // Create AAD (additional authentication data) from username and recipient ID
    public static byte[] makeAAD(String username, String recipientId) {
        return (username + ":" + recipientId).getBytes(StandardCharsets.UTF_8);
    }
}

