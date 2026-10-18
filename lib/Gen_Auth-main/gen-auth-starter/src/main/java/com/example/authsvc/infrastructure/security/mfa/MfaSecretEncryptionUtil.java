package com.example.authsvc.infrastructure.security.mfa;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for TOTP secrets persisted by {@link MfaService}.
 * Mirrors {@code KeyEncryptionUtil}'s scheme exactly (same
 * {@code [12-byte IV][GCM ciphertext+tag]} layout) but keyed by a
 * <b>separate</b> secret ({@code MFA_SECRET_ENCRYPTION_KEY}, not
 * {@code JWT_KEY_ENCRYPTION_SECRET}) — a compromise of one must never unlock
 * the other.
 */
public class MfaSecretEncryptionUtil {

    private static final String AES_GCM  = "AES/GCM/NoPadding";
    private static final int    IV_BYTES = 12;
    private static final int    TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom  random = new SecureRandom();

    public MfaSecretEncryptionUtil(String base64Secret) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("mfa.secret-encryption-key must be valid base64", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "mfa.secret-encryption-key must decode to exactly 32 bytes (AES-256), got " + decoded.length);
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_BYTES, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    public byte[] decrypt(byte[] ivAndCiphertext) {
        try {
            byte[] iv         = Arrays.copyOfRange(ivAndCiphertext, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(ivAndCiphertext, IV_BYTES, ivAndCiphertext.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }
}
