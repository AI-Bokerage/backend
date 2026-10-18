package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.BillingCycle;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PreviewPlanChangeRequest(
    @NotNull UUID tenantId,
    @NotNull UUID targetPpmPlanId,
    @NotNull String region,
    @NotNull BillingCycle cycle,
    String promoCode
) {}
