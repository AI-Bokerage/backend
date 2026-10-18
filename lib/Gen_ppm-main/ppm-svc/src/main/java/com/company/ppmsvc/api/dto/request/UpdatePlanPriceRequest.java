package com.company.ppmsvc.api.dto.request;

import java.math.BigDecimal;

/**
 * Request DTO for partially updating an existing plan price entry (PATCH semantics).
 *
 * <p>A {@code null} value means "leave unchanged".
 *
 * <p>The pricing identity fields ({@code planId}, {@code cycle}, {@code currency},
 * {@code region}, {@code effectiveFrom}) are intentionally absent — they are
 * immutable after creation and can never be changed via an update.
 */
public record UpdatePlanPriceRequest(

    BigDecimal amount,
    Boolean    taxInclusive,
    Boolean    active
) {}
