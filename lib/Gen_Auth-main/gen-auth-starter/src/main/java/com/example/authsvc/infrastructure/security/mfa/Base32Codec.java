package com.example.authsvc.infrastructure.security.mfa;

/**
 * RFC 4648 Base32 encode/decode (uppercase alphabet, no padding). Java's
 * standard library has no built-in Base32 (only Base64) — this hand-rolled
 * implementation exists purely to render/parse {@code otpauth://} URI
 * secrets, matching this codebase's existing pattern of implementing small,
 * self-contained crypto primitives directly (hand-rolled AES-GCM, PEM
 * parsing) rather than adding a dependency for something this narrow.
 */
public final class Base32Codec {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32Codec() {}

    public static String encode(byte[] data) {
        if (data.length == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(ALPHABET.charAt(index));
        }
        return result.toString();
    }

    public static byte[] decode(String base32) {
        String cleaned = base32.trim().toUpperCase();
        int bitCount = cleaned.length() * 5;
        byte[] result = new byte[bitCount / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int resultIndex = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            int charValue = ALPHABET.indexOf(cleaned.charAt(i));
            if (charValue < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + cleaned.charAt(i));
            }
            buffer = (buffer << 5) | charValue;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[resultIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
