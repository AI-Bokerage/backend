package com.company.audit.core.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Identifying context for a single hash chain partition, used to seed its genesis hash.
 *
 * @param partitionKey the key identifying this hash chain
 * @param createdAt the instant at which this partition was first created
 */
public record PartitionContext(String partitionKey, Instant createdAt) {

    /**
     * Validates that both fields are present.
     */
    public PartitionContext {
        Objects.requireNonNull(partitionKey, "partitionKey must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
