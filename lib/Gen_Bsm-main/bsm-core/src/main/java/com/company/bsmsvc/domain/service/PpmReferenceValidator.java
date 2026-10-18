package com.company.bsmsvc.domain.service;

import com.company.bsmsvc.domain.enums.PpmSnapshotStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.Subscription;
import org.springframework.stereotype.Component;

/**
 * Validates the structural integrity of a subscription's PPM reference fields.
 *
 * <p>The rule is all-or-nothing: either all four PPM fields are null (BSM-native
 * subscription) or all four are populated (PPM-backed). Any other combination is
 * a data integrity violation.
 *
 * <p>This validator makes no PPM HTTP calls — it checks the locally stored fields only.
 */
@Component
public class PpmReferenceValidator {

    /**
     * Returns the reference status without throwing.
     * Used by the diagnostics path where a partial reference is reported, not thrown.
     */
    public PpmSnapshotStatus check(Subscription sub) {
        int populated = countPopulated(sub);
        if (populated == 0) return PpmSnapshotStatus.NOT_PPM_BACKED;
        if (populated == 4) return PpmSnapshotStatus.HEALTHY;
        return PpmSnapshotStatus.PARTIAL_REFERENCE;
    }

    /** Returns true if the subscription was created through PPM checkout. */
    public boolean isPpmBacked(Subscription sub) {
        return sub.getPpmPlanId() != null;
    }

    /** Returns true when some but not all PPM reference fields are populated. */
    public boolean isPartial(Subscription sub) {
        int n = countPopulated(sub);
        return n > 0 && n < 4;
    }

    /**
     * Throws {@link BusinessRuleViolationException} if the subscription's PPM reference
     * fields are partially populated. Safe to call on both PPM-backed and BSM-native
     * subscriptions — BSM-native (all null) passes.
     */
    public void validateOrThrow(Subscription sub) {
        if (isPartial(sub)) {
            throw new BusinessRuleViolationException(
                "Subscription " + sub.getId() + " has an inconsistent PPM reference: "
                    + countPopulated(sub) + " of 4 fields are populated. "
                    + "PPM references must be all-null (BSM-native) or all-populated (PPM-backed).");
        }
    }

    private int countPopulated(Subscription sub) {
        int count = 0;
        if (sub.getPpmPlanId()            != null) count++;
        if (sub.getPpmPriceId()           != null) count++;
        if (sub.getPpmPlanVersionId()     != null) count++;
        if (sub.getPpmResolvedPriceMinor() != null) count++;
        return count;
    }
}
