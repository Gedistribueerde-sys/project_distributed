package org.example.crypto;

import com.google.protobuf.ByteString;
import org.example.proto.ChatProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

public class ChatCrypto {
    private static final Logger log = LoggerFactory.getLogger(ChatCrypto.class);
    static final private SecureRandom secureRandom = new SecureRandom();

    // Generate a new KeyInfo for initiating a chat (both send and receive)
    public static ChatProto.KeyInfo generateBumpKeyInfo(String senderUuid) throws Exception {
        long initialIdx = makeNewIdx();
        log.info("Generated Chat ID: {}", initialIdx);

        byte[] tagBytes = makeNewTag();

        SecretKey secretKey = generateChatKey();

        return ChatProto.KeyInfo.newBuilder()
                .setIdx(initialIdx)
                .setTag(ByteString.copyFrom(tagBytes))
                .setKey(ByteString.copyFrom(secretKey.getEncoded()))
                .setSenderUuid(senderUuid)
                .build();
    }

    public static SecretKey generateChatKey() throws NoSuchAlgorithmException {
        // generate AES-256 key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, secureRandom);
        return keyGen.generateKey();
    }

    public static long makeNewIdx() {
        return secureRandom.nextLong() & Long.MAX_VALUE; // makes sure this is positive
    }

    public static byte[] makeNewTag() {
        byte[] tagBytes = new byte[32];
        secureRandom.nextBytes(tagBytes);
        return tagBytes;
    }
    //Base64 maakt binaire data veilig overdraagbaar als tekst
    public static String tagToBase64(byte[] tagBytes) {
        return Base64.getEncoder().encodeToString(tagBytes);
    }

    // Encrypt payload using AES-GCM
    public static byte[] encryptPayloadBytes(byte[] payload, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // Generate random IV (12 bytes is recommended for GCM)
        byte[] iv = new byte[12]; // Maakt een initialisatievector (IV) van 12 bytes aan voor AES-GCM
        secureRandom.nextBytes(iv); // Vult de IV met cryptografisch veilige willekeurige bytes

        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv); // Definieert GCM-parameters met een 128-bit authenticatietag en de IV
        //Authenticatietag (Auth Tag) Wordt berekend tijdens encryptie -> data niet aangepast zonder detectie
        //Controleert integriteit en authenticiteit van de data
        //Niet door jou aangemaakt, maar door AES-GCM Zit automatisch in de ciphertext
        // IV voorkomt dat data voorspelbaar wordt, de auth tag voorkomt dat data vervalst wordt

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec); // Initialiseert de cipher in encryptiemodus met de geheime sleutel en GCM-instellingen

        byte[] ciphertext = cipher.doFinal(payload); // Versleutelt de payload en genereert de ciphertext

        // Prepend IV to ciphertext (needed for decryption)
        byte[] result = new byte[iv.length + ciphertext.length]; // Maakt een array aan om IV en ciphertext samen op te slaan
        System.arraycopy(iv, 0, result, 0, iv.length); // Kopieert de IV naar het begin van het resultaat
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length); // Plakt de ciphertext direct na de IV

        return result; // Geeft het gecombineerde resultaat (IV + ciphertext) terug

    }

    public static byte[] decryptPayloadBytes(byte[] encryptedPayload, SecretKey secretKey) throws Exception {

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // Maakt een Cipher aan voor AES in GCM-modus (met authenticatie) zonder padding

        // Extract IV from the beginning of the encrypted payload
        byte[] iv = new byte[12]; // Reserveert ruimte voor de IV (12 bytes, zoals bij encryptie gebruikt)
        System.arraycopy(encryptedPayload, 0, iv, 0, iv.length); // Kopieert de eerste 12 bytes uit encryptedPayload naar iv

                // Extract the actual ciphertext
        byte[] ciphertext = new byte[encryptedPayload.length - iv.length]; // Maakt array voor de rest (ciphertext + auth tag zit hierin)
        System.arraycopy(encryptedPayload, iv.length, ciphertext, 0, ciphertext.length); // Kopieert alles na de IV naar ciphertext

        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv); // Stelt GCM in met 128-bit tag-lengte en de juiste IV voor decryptie
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec); // Initialiseert de cipher om te decrypten met dezelfde sleutel en IV

        return cipher.doFinal(ciphertext); // Decrypt + controleert de auth tag; faalt (exception) als data/sleutel/IV niet klopt


    }

    // Derive a new SecretKey from an old one using SHA-256
    public static SecretKey makeNewSecretKey(SecretKey oldKey) throws NoSuchAlgorithmException {
        byte[] oldBytes = oldKey.getEncoded();

        // Hash the old key bytes using SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); // Maakt een MessageDigest-object aan dat het SHA-256 hash-algoritme gebruikt
        byte[] hash = digest.digest(oldBytes);

        // Use the hash as the new key
        return new SecretKeySpec(hash, 0, 32, "AES"); // GCM/NoPadding heeft hier geen betekenis het is een sleutel

    }
}

