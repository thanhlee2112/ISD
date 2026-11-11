package com.example.CMS.Common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts numeric database IDs so raw sequential IDs are not exposed to clients.
 * Uses AES-GCM, so tampered or forged values are rejected instead of decrypting to another ID.
 */
@Component
public class IdEncryptor {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public IdEncryptor(@Value("${app.id-encryption-key}") String hexKey) {
        byte[] key = hexToBytes(hexKey);
        if (key.length != 16 && key.length != 32) {
            throw new IllegalStateException(
                    "ID_ENCRYPTION_KEY must be 32 or 64 hex characters (64 recommended, AES-256)");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0 || !hex.matches("[0-9a-fA-F]*")) {
            throw new IllegalStateException("ID_ENCRYPTION_KEY must be a hexadecimal string");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    public String encrypt(Long id) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(String.valueOf(id).getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt id", e);
        }
    }

    /**
     * @throws IllegalArgumentException if the value is not a valid encrypted id
     */
    public Long decrypt(String encryptedId) {
        if (encryptedId == null || encryptedId.isBlank()) {
            throw new IllegalArgumentException("Invalid id");
        }
        try {
            byte[] in = Base64.getUrlDecoder().decode(encryptedId);
            if (in.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid id");
            }
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, in, 0, IV_LENGTH));
            byte[] decrypted = cipher.doFinal(in, IV_LENGTH, in.length - IV_LENGTH);
            return Long.valueOf(new String(decrypted, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid id", e);
        }
    }
}
