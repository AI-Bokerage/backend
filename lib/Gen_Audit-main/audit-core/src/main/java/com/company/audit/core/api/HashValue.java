package com.company.audit.core.api;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * An immutable wrapper around a fixed-size cryptographic hash's raw bytes.
 *
 * <p>This type exists so that raw {@code byte[]} hash values never cross a public API boundary:
 * arrays are mutable and have reference-based {@code equals}/{@code hashCode}, which would make
 * {@link ChainedRecord} equality silently incorrect. Wrapping in {@code HashValue} fixes both
 * problems and gives callers a safe, immutable value type.
 */
public final class HashValue {

    private final byte[] bytes;

    private HashValue(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Creates a {@code HashValue} from the given bytes, defensively cloning the input so that
     * later mutation of the caller's array cannot affect this instance.
     *
     * @param bytes the raw hash bytes; must not be {@code null}
     * @return a new immutable {@code HashValue}
     */
    public static HashValue of(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        return new HashValue(bytes.clone());
    }

    /**
     * Returns a defensive copy of this hash's raw bytes.
     *
     * @return a new array containing the raw hash bytes
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    /**
     * Returns this hash rendered as a lowercase hexadecimal string.
     *
     * @return the hexadecimal representation of the raw bytes
     */
    public String hex() {
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HashValue other)) {
            return false;
        }
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return hex();
    }
}
