package com.vault.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Component
public class TotpUtil {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int WINDOW_SIZE = 1; // 1 step before, 1 step after

    public String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20]; // 160 bits (recommended for SHA1)
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(ALPHABET.charAt((b & 0xFF) % ALPHABET.length()));
        }
        return sb.toString();
    }

    public String getQrCodeUrl(String secretKey, String email) {
        try {
            String issuer = "VAULT";
            return "otpauth://totp/" 
                    + URLEncoder.encode(issuer + ":" + email, StandardCharsets.UTF_8.name())
                    + "?secret=" + secretKey
                    + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException("Error encoding QR code URL", e);
        }
    }

    public boolean verifyCode(String secretKey, String codeStr) {
        if (codeStr == null || !codeStr.matches("\\d{6}")) {
            return false;
        }
        int code = Integer.parseInt(codeStr);
        byte[] decodedKey = decodeBase32(secretKey);

        long currentUnixTime = System.currentTimeMillis() / 1000L;
        long currentTimeStep = currentUnixTime / TIME_STEP_SECONDS;

        for (int i = -WINDOW_SIZE; i <= WINDOW_SIZE; i++) {
            if (getCode(decodedKey, currentTimeStep + i) == code) {
                return true;
            }
        }
        return false;
    }

    private static int getCode(byte[] secret, long timeIndex) {
        try {
            byte[] data = new byte[8];
            long value = timeIndex;
            for (int i = 8; i-- > 0; value >>>= 8) {
                data[i] = (byte) (value & 0xFF);
            }

            SecretKeySpec signKey = new SecretKeySpec(secret, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signKey);
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0xF;
            int truncatedHash = 0;
            for (int i = 0; i < 4; ++i) {
                truncatedHash <<= 8;
                truncatedHash |= (hash[offset + i] & 0xFF);
            }
            truncatedHash &= 0x7FFFFFFF;
            truncatedHash %= 1000000;
            return truncatedHash;
        } catch (Exception e) {
            throw new RuntimeException("Error calculating TOTP code", e);
        }
    }

    private static byte[] decodeBase32(String base32) {
        String cleaned = base32.toUpperCase().replace("=", "").replace(" ", "");
        int len = cleaned.length();
        byte[] bytes = new byte[len * 5 / 8];
        int val = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < len; i++) {
            char c = cleaned.charAt(i);
            int digit = ALPHABET.indexOf(c);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            val = (val << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                bytes[index++] = (byte) ((val >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return bytes;
    }
}
