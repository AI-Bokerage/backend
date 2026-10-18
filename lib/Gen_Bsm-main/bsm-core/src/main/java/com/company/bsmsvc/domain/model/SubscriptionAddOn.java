package com.company.bsmsvc.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Domain model for a PPM add-on that has been purchased and attached to a subscription.
 *
 * <p>Grandfathering invariant: {@code ppmResolvedPriceMinor} is locked at purchase time
 * and never updated.  Renewal invoices always use this locked amount, never re-querying PPM.
 *
 * <p>Setting {@code active = false} removes the add-on from future renewals without
 * deleting the row (full audit trail is preserved).
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionAddOn {

    private UUID    id;
    private UUID    subscriptionId;
    private UUID    tenantId;
    private UUID    ppmAddOnId;
    private UUID    ppmAddOnPriceId;
    private Long    ppmResolvedPriceMinor;
    private boolean active;
    private Instant createdAt;
    private String  createdBy;
}
