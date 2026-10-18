package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.common.BillingCycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/ppm/referrals/quote}. Unlike the
 * coupon quote path, {@code customerContext} is required — referral pricing
 * always evaluates conditions.
 */
public record ReferralQuoteRequest(

    @NotNull
    UUID planId,

    @NotBlank
    String region,

    @NotBlank
    String currency,

    @NotNull
    BillingCycle cycle,

    @NotBlank
    String referralCode,

    @NotNull @Valid
    CustomerContextRequest customerContext
) {}
