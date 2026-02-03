package com.vault.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTE = 12;
    private static final int TAG_LENGTH_BIT = 128;

    private static byte[] secretKeyBytes;

    @Value("${app.aes.secret-key}")
    public void setSecretKey(String secretKey) {
        if (secretKey == null || secretKey.length() < 16) {
            throw new IllegalArgumentException("AES secret key must be at least 16 characters long");
        }
        // Ensure key is 16, 24, or 32 bytes.
        byte[] raw = secretKey.getBytes();
        byte[] key = new byte[32]; // Default to 256-bit AES
        System.arraycopy(raw, 0, key, 0, Math.min(raw.length, key.length));
        secretKeyBytes = key;
    }

    public static String encrypt(String value) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] encryptedText = cipher.doFinal(value.getBytes());

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedText.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Error occurred during encryption", e);
        }
    }

    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] encryptedText = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] decryptedText = cipher.doFinal(encryptedText);
            return new String(decryptedText);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred during decryption", e);
        }
    }

    private static byte[] getKeyBytes() {
        if (secretKeyBytes == null) {
            // Spring hasn't initialized the bean yet, fetch from Env
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
