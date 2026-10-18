package com.company.audit.core.internal.crypto;

import com.company.audit.core.api.AuditConstants;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.PartitionContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes the hashes that link records together into a tamper-evident chain.
 */
public final class ChainHasher {

    /**
     * Creates a new hasher.
     */
    public ChainHasher() {
    }

    /**
     * Computes the genesis hash for a partition, which serves as the {@code prevEventHash} of
     * that partition's first record.
     *
     * @param partitionContext the partition to compute a genesis hash for
     * @return the genesis hash
     */
    public HashValue computeGenesis(PartitionContext partitionContext) {
        String seed = AuditConstants.GENESIS_PREFIX
                + partitionContext.partitionKey()
                + partitionContext.createdAt().toString();
        return HashValue.of(digest(seed.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Computes the hash of a canonicalized event payload.
     *
     * @param canonicalJson the canonical JSON form of an event's payload
     * @return the payload hash
     */
    public HashValue computePayloadHash(String canonicalJson) {
        return HashValue.of(digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Computes the event hash linking the previous hash and this record's payload hash together.
     *
     * @param prevHash the previous record's event hash, or the partition's genesis hash
     * @param payloadHash this record's payload hash
     * @return the event hash
     */
    public HashValue computeEventHash(HashValue prevHash, HashValue payloadHash) {
        byte[] prevBytes = prevHash.bytes();
        byte[] payloadBytes = payloadHash.bytes();
        ByteBuffer buffer = ByteBuffer.allocate(prevBytes.length + payloadBytes.length);
        buffer.put(prevBytes).put(payloadBytes);
        return HashValue.of(digest(buffer.array()));
    }

    private byte[] digest(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(AuditConstants.HASH_ALGORITHM);
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Required hash algorithm unavailable: " + AuditConstants.HASH_ALGORITHM, e);
        }
    }
}
