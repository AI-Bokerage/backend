package com.company.ppmsvc.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for assigning or replacing plan restrictions on a promo code.
 *
 * <p>{@link Set} is used rather than {@link java.util.List} to provide
 * BR-P3 idempotency at the binding layer: duplicate plan IDs in the request
 * are automatically collapsed to a single assignment before the service
 * processes them.
 */
public record AssignPromoPlansRequest(

    @NotEmpty
    Set<UUID> planIds
) {}
