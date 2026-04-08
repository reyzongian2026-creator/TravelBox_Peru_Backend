package com.tuempresa.storage.shared.infrastructure.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

@Service
@Lazy(false)
public class SensitiveDataService {

    private static final Logger LOG = LoggerFactory.getLogger(SensitiveDataService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    public enum SensitiveFieldType {
        DNI,
        RUC,
        PASSPORT,
        PHONE,
        ADDRESS,
        GPS_COORDINATES,
        EMERGENCY_CONTACT_NAME,
        EMERGENCY_CONTACT_PHONE,
        CREDIT_CARD_NUMBER,
        CREDIT_CARD_CVV,
        CREDIT_CARD_EXPIRY,
        BANK_ACCOUNT,
        IBAN,
        NOTES,
        SPECIAL_INSTRUCTIONS
    }

    private static final Set<SensitiveFieldType> ALWAYS_ENCRYPTED = EnumSet.of(
            SensitiveFieldType.DNI,
            SensitiveFieldType.RUC,
            SensitiveFieldType.PASSPORT,
            SensitiveFieldType.PHONE,
            SensitiveFieldType.ADDRESS,
            SensitiveFieldType.GPS_COORDINATES,
            SensitiveFieldType.EMERGENCY_CONTACT_NAME,
            SensitiveFieldType.EMERGENCY_CONTACT_PHONE,
            SensitiveFieldType.CREDIT_CARD_NUMBER,
            SensitiveFieldType.CREDIT_CARD_CVV,
            SensitiveFieldType.CREDIT_CARD_EXPIRY,
            SensitiveFieldType.BANK_ACCOUNT,
            SensitiveFieldType.IBAN,
            SensitiveFieldType.NOTES,
            SensitiveFieldType.SPECIAL_INSTRUCTIONS
    );

    @Value("${app.security.encryption-key:}")
    private String encryptionKeyBase64;

    private SecretKey encryptionKey;

    @PostConstruct
    public void init() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            LOG.warn("No encryption key configured. Using default key for development only!");
            encryptionKeyBase64 = "ZGV2LWRlZmF1bHQta2V5LWZvci10ZXN0aW5nLW9ubHk=";
        }
        
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        
        if (keyBytes.length != 32) {
            LOG.error("Encryption key must be 32 bytes (256 bits) for AES-256!");
            throw new IllegalStateException("Invalid encryption key length");
        }
        
        this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
        LOG.info("SensitiveDataService initialized with AES-256-GCM encryption");
    }

    public boolean shouldEncrypt(String fieldName) {
        try {
            SensitiveFieldType type = SensitiveFieldType.valueOf(fieldName.toUpperCase());
            return ALWAYS_ENCRYPTED.contains(type);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean shouldEncrypt(SensitiveFieldType type) {
        return ALWAYS_ENCRYPTED.contains(type);
    }

    public String encrypt(String plainText) {
        return encrypt(plainText, null);
    }

    public String encrypt(String plainText, SensitiveFieldType type) {
        if (plainText == null) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            LOG.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText) {
        return decrypt(encryptedText, null);
    }

    public String decrypt(String encryptedText, SensitiveFieldType type) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public String mask(String value, SensitiveFieldType type) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        switch (type) {
            case DNI:
            case RUC:
            case PASSPORT:
                return maskDocument(value);

            case PHONE:
            case EMERGENCY_CONTACT_PHONE:
                return maskPhone(value);

            case ADDRESS:
                return maskAddress(value);

            case GPS_COORDINATES:
                return maskGpsCoordinates(value);

            case CREDIT_CARD_NUMBER:
                return maskCreditCard(value);

            case BANK_ACCOUNT:
                return maskBankAccount(value);

            case IBAN:
                return maskIban(value);

            case EMERGENCY_CONTACT_NAME:
            case NOTES:
            case SPECIAL_INSTRUCTIONS:
            case CREDIT_CARD_CVV:
            case CREDIT_CARD_EXPIRY:
            default:
                return "****";
        }
    }

    public String maskDocument(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "****-" + value.substring(value.length() - 4);
    }

    public String maskPhone(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "****-" + value.substring(value.length() - 4);
    }

    public String maskAddress(String value) {
        if (value == null) {
            return "****";
        }
        String[] parts = value.split(",");
        if (parts.length >= 2) {
            return "****, " + parts[parts.length - 1].trim();
        }
        return "****";
    }

    public String maskGpsCoordinates(String value) {
        if (value == null) {
            return "****, ****";
        }
        return "****, ****";
    }

    public String maskCreditCard(String value) {
        if (value == null || value.length() < 4) {
            return "****-****-****-****";
        }
        return "****-****-****-" + value.substring(value.length() - 4);
    }

    public String maskBankAccount(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "****-" + value.substring(value.length() - 4);
    }

    public String maskIban(String value) {
        if (value == null || value.length() < 6) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts.length > 1 ? parts[1] : "";

        if (local.length() <= 1) {
            return "****@" + domain;
        }
        return local.charAt(0) + "****@" + domain;
    }
}
