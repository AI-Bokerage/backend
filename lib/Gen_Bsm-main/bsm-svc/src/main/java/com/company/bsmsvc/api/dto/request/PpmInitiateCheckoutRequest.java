package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Initiate PPM-backed checkout request")
public record PpmInitiateCheckoutRequest(

    @NotNull
    UUID tenantId,

    @NotNull
    UUID ppmPlanId,

    @NotNull
    BillingCycle billingCycle,

    @NotBlank
    String region,

    String promoCode,

    @NotBlank
    String successUrl,

    @NotBlank
    String cancelUrl,

    @NotBlank
    String performedBy,

    String reason
) {}
