package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PurchaseAddOnRequest(
    @NotNull  UUID        tenantId,
    @NotNull  UUID        ppmAddOnId,
    @NotBlank String      region,
    @NotNull  BillingCycle cycle,
    @NotBlank String      successUrl,
    @NotBlank String      cancelUrl,
    @NotBlank String      performedBy
) {}
