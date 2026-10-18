package com.company.audit.core.api.enums;

/**
 * The outcome status of a chain verification run, as reported in a
 * {@link com.company.audit.core.api.VerificationResult}.
 */
public enum AuditChainStatus {

    /** The chain was verified and no inconsistency was found. */
    OK,

    /** Verification encountered a break in hash linkage at a specific sequence number. */
    BREAK_AT_SEQ,

    /** A record's stored event hash did not match its recomputed hash. */
    HASH_MISMATCH,

    /** Two or more records were found sharing the same sequence number. */
    DUPLICATE_SEQ,

    /** Verification could not complete within the allotted time. */
    TIMEOUT
}
