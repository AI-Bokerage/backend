package com.company.audit.core.api;

/**
 * Fixed constants shared across the audit ledger's public surface.
 *
 * <p>These constants (together with {@link HashValue}) are the only information a third party
 * needs to independently re-verify an exported chain, without depending on any implementation
 * class in this library.
 */
public final class AuditConstants {

    /**
     * The hash algorithm used throughout the ledger, as passed to
     * {@link java.security.MessageDigest#getInstance(String)}.
     *
     * <p>This is a fixed constant, not a pluggable strategy: changing the algorithm would
     * invalidate every previously anchored record regardless of how the code is structured, so
     * there is no real flexibility to buy by making it configurable.
     */
    public static final String HASH_ALGORITHM = "SHA-256";

    /**
     * The prefix mixed into the genesis hash computation for a partition, ensuring genesis
     * hashes are distinguishable from ordinary event hashes even if inputs collide.
     */
    public static final String GENESIS_PREFIX = "AUDIT_GENESIS:";

    private AuditConstants() {
    }
}
