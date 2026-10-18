package com.example.authsvc.infrastructure.security.mfa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaSecretEncryptionUtilTest {

    private MfaSecretEncryptionUtil util;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        util = new MfaSecretEncryptionUtil(Base64.getEncoder().encodeToString(key));
    }

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        byte[] plaintext = "totp-secret-bytes-here!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = util.encrypt(plaintext);
        byte[] decrypted  = util.decrypt(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
        assertThat(ciphertext).isNotEqualTo(plaintext);
    }

    @Test
    void encrypt_sameInputTwice_producesDifferentCiphertext() {
        byte[] plaintext = "same-input".getBytes(StandardCharsets.UTF_8);

        byte[] first  = util.encrypt(plaintext);
        byte[] second = util.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second); // random IV each time
    }

    @Test
    void constructor_invalidBase64_throws() {
        assertThatThrownBy(() -> new MfaSecretEncryptionUtil("not-valid-base64!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be valid base64");
    }

    @Test
    void constructor_wrongKeyLength_throws() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new MfaSecretEncryptionUtil(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to exactly 32 bytes");
    }
}
