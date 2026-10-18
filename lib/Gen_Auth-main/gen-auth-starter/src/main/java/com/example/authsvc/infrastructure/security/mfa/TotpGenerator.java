package com.example.authsvc.infrastructure.security.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;

/**
 * RFC 6238 TOTP (time-based one-time password) over HMAC-SHA1, RFC 4226's
 * dynamic-truncation scheme. Hand-rolled, no new dependency — matches this
 * codebase's existing pattern for small, self-contained crypto primitives.
 *
 * <p>{@code generate(secret, instant)} is the convenience overload used
 * everywhere in this codebase: 30-second step, 6-digit codes (the
 * Google-Authenticator-compatible convention). {@link MfaService} additionally
 * checks the ±1 step window (previous/current/next 30s window) for clock-drift
 * tolerance when verifying a submitted code — that tolerance lives in the
 * caller, not here; this class only computes the code for one exact instant.
 */
public final class TotpGenerator {

    private static final String HMAC_SHA1 = "HmacSHA1";

    private TotpGenerator() {}

    public static String generate(byte[] secret, Instant instant) {
        return generate(secret, instant, 30, 6);
    }

    public static String generate(byte[] secret, Instant instant, int stepSeconds, int digits) {
        long timeStep = instant.getEpochSecond() / stepSeconds;
        byte[] counterBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (timeStep & 0xFF);
            timeStep >>= 8;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(secret, HMAC_SHA1));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int truncated = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int modDivisor = (int) Math.pow(10, digits);
            int code = truncated % modDivisor;
            return String.format("%0" + digits + "d", code);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate TOTP code", e);
        }
    }
}
