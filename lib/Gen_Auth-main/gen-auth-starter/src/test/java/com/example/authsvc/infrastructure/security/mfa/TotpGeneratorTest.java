package com.example.authsvc.infrastructure.security.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test vectors from RFC 6238 Appendix B, Table 1 (SHA-1, 30-second step,
 * secret = ASCII "12345678901234567890" used directly as the raw HMAC key —
 * NOT base32-decoded, since the RFC's published secret already IS the raw
 * key material). The RFC's table publishes 8-digit codes; this generator
 * produces 6-digit codes (the Google-Authenticator-compatible convention
 * this codebase targets), which is simply {@code truncatedValue mod 10^6} —
 * i.e. the last 6 digits of the RFC's published 8-digit value for the same
 * time/secret. Both are the same underlying computation truncated to a
 * different digit count.
 */
class TotpGeneratorTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void generate_rfc6238Vector_time59() {
        assertThat(TotpGenerator.generate(SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
    }

    @Test
    void generate_rfc6238Vector_time1111111109() {
        assertThat(TotpGenerator.generate(SECRET, Instant.ofEpochSecond(1111111109L))).isEqualTo("081804");
    }

    @Test
    void generate_rfc6238Vector_time1111111111() {
        assertThat(TotpGenerator.generate(SECRET, Instant.ofEpochSecond(1111111111L))).isEqualTo("050471");
    }

    @Test
    void generate_rfc6238Vector_time1234567890() {
        assertThat(TotpGenerator.generate(SECRET, Instant.ofEpochSecond(1234567890L))).isEqualTo("005924");
    }

    @Test
    void generate_rfc6238Vector_time2000000000() {
        assertThat(TotpGenerator.generate(SECRET, Instant.ofEpochSecond(2000000000L))).isEqualTo("279037");
    }

    @Test
    void generate_differentSecrets_produceDifferentCodes() {
        byte[] otherSecret = "09876543210987654321".getBytes(StandardCharsets.US_ASCII);
        String code1 = TotpGenerator.generate(SECRET, Instant.ofEpochSecond(59));
        String code2 = TotpGenerator.generate(otherSecret, Instant.ofEpochSecond(59));
        assertThat(code1).isNotEqualTo(code2);
    }
}
