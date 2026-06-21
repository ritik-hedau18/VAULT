package com.vault.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTE = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final SecureRandom secureRandom = new SecureRandom();

    private static byte[] secretKeyBytes;

    @Value("${app.aes.secret-key}")
    public void setSecretKey(String secretKey) {
        if (secretKey == null || secretKey.length() < 16) {
            throw new IllegalArgumentException("AES secret key must be at least 16 characters long");
        }
        byte[] raw = secretKey.getBytes();
        byte[] key = new byte[32]; // Default to 256-bit AES
        System.arraycopy(raw, 0, key, 0, Math.min(raw.length, key.length));
        secretKeyBytes = key;
    }

    public static String encrypt(String value) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encryptedText = cipher.doFinal(value.getBytes());

            // Prepended IV before ciphertext
            byte[] combined = new byte[iv.length + encryptedText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedText, 0, combined, iv.length, encryptedText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred during encryption", e);
        }
    }

    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedValue);
            if (combined.length < IV_LENGTH_BYTE) {
                throw new IllegalArgumentException("Invalid encrypted text: too short");
            }

            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTE);

            int ciphertextLen = combined.length - IV_LENGTH_BYTE;
            byte[] ciphertext = new byte[ciphertextLen];
            System.arraycopy(combined, IV_LENGTH_BYTE, ciphertext, 0, ciphertextLen);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decryptedText = cipher.doFinal(ciphertext);
            return new String(decryptedText);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred during decryption", e);
        }
    }

    private static byte[] getKeyBytes() {
        if (secretKeyBytes == null) {
            String envKey = System.getenv("AES_SECRET_KEY");
            if (envKey == null) {
                envKey = "SuperSecretVaultKeyEncryptAtRest12"; // Fallback default
            }
            byte[] raw = envKey.getBytes();
            byte[] key = new byte[32];
            System.arraycopy(raw, 0, key, 0, Math.min(raw.length, key.length));
            secretKeyBytes = key;
        }
        return secretKeyBytes;
    }

    @Converter
    public static class AesEncryptor implements AttributeConverter<String, String> {
        @Override
        public String convertToDatabaseColumn(String attribute) {
            return encrypt(attribute);
        }

        @Override
        public String convertToEntityAttribute(String dbData) {
            return decrypt(dbData);
        }
    }
}
