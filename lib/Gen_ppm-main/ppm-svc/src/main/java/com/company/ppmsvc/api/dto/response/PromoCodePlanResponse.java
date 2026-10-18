package com.company.ppmsvc.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO for a promo code ↔ plan restriction row.
 *
 * <p>Exposes the mapping identity and creation timestamp. The promo code UUID
 * is included because this DTO may appear in contexts where only the plan ID
 * is known from the request path.
 */
public record PromoCodePlanResponse(
    UUID    id,
    UUID    promoCodeId,
    UUID    planId,
    Instant createdAt
) {}
