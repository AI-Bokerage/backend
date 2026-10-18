package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.common.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a new plan price entry.
 *
 * <p>{@code taxInclusive} defaults to {@code false} when absent (BR-5).
 * {@code effectiveFrom} may be a future date — future-dated prices are valid
 * and required for scheduled price changes and versioning.
 */
public record CreatePlanPriceRequest(

    @NotNull
    UUID planId,

    @NotNull
    BillingCycle cycle,

    @NotBlank
    String currency,

    @NotBlank
    String region,

    @NotNull
    BigDecimal amount,

    Boolean taxInclusive,

    @NotNull
    LocalDate effectiveFrom
) {}
