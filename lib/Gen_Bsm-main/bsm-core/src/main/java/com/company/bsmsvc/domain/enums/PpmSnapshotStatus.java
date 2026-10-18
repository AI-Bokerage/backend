package com.company.bsmsvc.domain.enums;

/**
 * Result of a PPM snapshot integrity check on a subscription.
 *
 * <p>{@code HEALTHY} and {@code NOT_PPM_BACKED} are normal states.
 * All other values indicate a condition that warrants investigation.
 */
public enum PpmSnapshotStatus {

    /** All four PPM reference fields are populated and the plan still exists in PPM. */
    HEALTHY,

    /** Subscription was not created through PPM checkout — all four PPM fields are null. */
    NOT_PPM_BACKED,

    /** PPM fields are partially populated (1–3 of 4 non-null). Should never occur in production. */
    PARTIAL_REFERENCE,

    /** PPM plan no longer responds — the plan may have been deleted from the catalog. */
    MISSING_PLAN,

    /** PPM service was unreachable during the check — result may be stale. */
    PPM_UNAVAILABLE
}
