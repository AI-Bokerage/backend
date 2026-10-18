package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.common.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/ppm/prices/resolve}.
 *
 * <p>{@code region} and {@code currency} are normalised to uppercase with
 * leading/trailing whitespace stripped before the price lookup.
 */
public record ResolvePriceRequest(

    @NotNull
    UUID planId,

    @NotBlank
    String region,

    @NotBlank
    String currency,

    @NotNull
    BillingCycle cycle
) {}
