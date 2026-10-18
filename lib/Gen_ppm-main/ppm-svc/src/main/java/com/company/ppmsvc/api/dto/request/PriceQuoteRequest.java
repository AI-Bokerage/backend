package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.common.BillingCycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/ppm/quotes}.
 *
 * <p>{@code couponCode} is optional — omit it to get the base price with no
 * discount applied ({@code reason=no_coupon}).
 *
 * <p>{@code customerContext} is optional. When supplied, the pricing engine
 * additionally evaluates the promotion's conditions (plan restriction,
 * eligibility, per-user usage cap) via {@code quoteWithCustomer}. When
 * omitted, the Phase-0 {@code quote(...)} path is used and conditions are
 * skipped — this keeps existing callers working unchanged.
 */
public record PriceQuoteRequest(

    @NotNull
    UUID planId,

    @NotBlank
    String region,

    @NotBlank
    String currency,

    @NotNull
    BillingCycle cycle,

    String couponCode,

    @Valid
    CustomerContextRequest customerContext
) {}
