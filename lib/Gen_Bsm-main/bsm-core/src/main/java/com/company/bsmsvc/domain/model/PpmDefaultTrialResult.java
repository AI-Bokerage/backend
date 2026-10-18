package com.company.bsmsvc.domain.model;

import java.util.UUID;

/**
 * BSM-side representation of PPM's DefaultTrialResponse.
 *
 * <p>Returned by {@code GET /api/v1/ppm/plans/default-trial}.
 * Used by {@link com.company.bsmsvc.messaging.TenantCreatedConsumer} to resolve
 * the trial plan version when provisioning a new tenant's TRIALING subscription
 * without reading BSM's local plan catalog.
 */
public record PpmDefaultTrialResult(
    UUID   planId,
    String planCode,
    UUID   planVersionId
) {}
