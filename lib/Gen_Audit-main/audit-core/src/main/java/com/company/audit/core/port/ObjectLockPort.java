package com.company.audit.core.port;

import com.company.audit.core.api.HashValue;
import java.time.Instant;

/**
 * A driven port for publishing a write-once-read-many ("WORM") anchor of a partition's chain
 * tip to immutable object storage, proving at a point in time that the chain had not yet been
 * altered.
 *
 * <p>Returns an opaque {@code String} storage reference rather than a rich, named type: what a
 * reference actually contains is entirely up to the adapter (an S3 adapter might pack an ETag
 * and VersionId into it; a different backend might return something else entirely). Modeling
 * those storage-specific concepts here would leak infrastructure into a module that is supposed
 * to know nothing about it — storage-specific richness, if ever needed, belongs in whichever
 * adapter implements this port, not in this port's contract.
 */
public interface ObjectLockPort {

    /**
     * Publishes an anchor recording {@code partitionKey}'s chain tip at {@code publishedAt}.
     *
     * @param partitionKey the partition being anchored
     * @param tipHash the partition's current chain tip event hash
     * @param publishedAt the instant this anchor is being published
     * @return an opaque, adapter-defined reference to the published anchor
     */
    String publishAnchor(String partitionKey, HashValue tipHash, Instant publishedAt);
}
