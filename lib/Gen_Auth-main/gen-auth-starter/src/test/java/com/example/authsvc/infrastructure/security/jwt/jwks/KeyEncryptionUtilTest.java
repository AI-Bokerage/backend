package com.example.authsvc.infrastructure.security.jwt.jwks;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyEncryptionUtilTest {

    private static String validSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void roundTripsPlaintext() {
        KeyEncryptionUtil util = new KeyEncryptionUtil(validSecret());
        byte[] plaintext = "super-secret-private-key-bytes".getBytes();

        byte[] ciphertext = util.encrypt(plaintext);
        byte[] decrypted  = util.decrypt(ciphertext);

        assertArrayEquals(plaintext, decrypted);
        assertNotEquals(new String(plaintext), new String(ciphertext));
    }

    @Test
    void rejectsWrongLengthSecret() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new KeyEncryptionUtil(tooShort));
    }

    @Test
    void rejectsNonBase64Secret() {
        assertThrows(IllegalStateException.class, () -> new KeyEncryptionUtil("not-valid-base64!!!"));
    }
}
