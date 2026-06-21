package com.vault.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH_BYTE = 16;
    private static final byte[] FIXED_IV = new byte[IV_LENGTH_BYTE]; // Deterministic IV (all zeros)

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
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(FIXED_IV);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encryptedText = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encryptedText);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred during encryption", e);
        }
    }

    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(FIXED_IV);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decryptedText = cipher.doFinal(decoded);
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
