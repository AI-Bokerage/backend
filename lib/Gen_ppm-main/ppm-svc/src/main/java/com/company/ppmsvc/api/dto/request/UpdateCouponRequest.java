package com.company.ppmsvc.api.dto.request;

import java.util.UUID;

/**
 * Request DTO for partially updating an existing coupon (PATCH semantics).
 * A {@code null} value means "leave unchanged".
 *
 * <p>{@code code} is intentionally absent — it is immutable after creation.
 */
public record UpdateCouponRequest(

    UUID    promotionId,
    Boolean active
) {}
