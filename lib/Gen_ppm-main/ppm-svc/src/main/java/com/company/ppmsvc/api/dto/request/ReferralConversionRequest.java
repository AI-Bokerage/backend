package com.company.ppmsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReferralConversionRequest(

    @NotBlank
    String referralCode,

    @NotBlank
    String referredCustomerId
) {}
