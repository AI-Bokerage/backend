package com.company.ppmsvc.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO representing a single coupon entry.
 *
 * <p>The following fields are intentionally absent to avoid leaking internal
 * state: {@code version}, {@code deletedAt}, {@code createdBy}, {@code updatedBy}.
 */
public record CouponResponse(

    UUID    id,
    String  code,
    UUID    promotionId,
    Boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
