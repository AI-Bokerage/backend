package com.example.authsvc.infrastructure.security.refresh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RefreshTokenHashUtil {

    private RefreshTokenHashUtil() {}

    public static String hash(String plaintextToken) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(plaintextToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean matches(String plaintextToken, String storedHash) {
        return hash(plaintextToken).equals(storedHash);
    }
}
