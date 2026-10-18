package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PpmSnapshotStatus;
import java.util.UUID;

/**
 * Read-only diagnostic result for a subscription's PPM snapshot integrity.
 *
 * <p>{@code status} is the authoritative classification. The remaining fields
 * are populated on a best-effort basis: fields that require PPM reachability
 * are {@code null} when {@code status == PPM_UNAVAILABLE}.
 */
public record PpmSnapshotDiagnostics(

    UUID subscriptionId,

    /** True iff the subscription was created through PPM checkout (any 4 fields non-null). */
    boolean ppmBacked,

    PpmSnapshotStatus status,

    /**
     * True when the subscription's locked plan version differs from the current catalog version.
     * Indicates the subscriber is on a grandfathered price. {@code null} when PPM is unavailable
     * or the subscription is not PPM-backed.
     */
    Boolean grandfathered,

    /** Latest catalog version number from PPM-SVC at time of check. {@code null} if unavailable. */
    Integer catalogVersionNo,

    /** Version UUID locked onto this subscription at checkout time. {@code null} if not PPM-backed. */
    UUID lockedVersionId,

    /** Latest plan-version UUID from PPM-SVC at time of check. {@code null} if unavailable. */
    UUID latestVersionId
) {}
