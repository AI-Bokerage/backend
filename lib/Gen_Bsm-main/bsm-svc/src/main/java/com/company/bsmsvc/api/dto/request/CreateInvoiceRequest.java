package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Manual invoice creation request. The billing period, currency, and line items are "
    + "automatically derived from the active subscription. No manual overrides are accepted.")
public record CreateInvoiceRequest(
    @NotNull
    UUID tenantId,

    @NotNull
    UUID subscriptionId
) {
}
