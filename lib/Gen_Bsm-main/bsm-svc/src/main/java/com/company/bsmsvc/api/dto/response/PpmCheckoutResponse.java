package com.company.bsmsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "PPM-backed checkout initiation response")
public record PpmCheckoutResponse(

    @Schema(description = "Created subscription ID")
    UUID subscriptionId,

    @Schema(description = "Generated invoice ID")
    UUID invoiceId,

    @Schema(description = "Payment provider checkout URL — redirect the user here")
    String checkoutUrl,

    @Schema(description = "Payment provider session ID")
    String sessionId,

    @Schema(description = "PPM-resolved price in minor units (e.g. paise, cents)")
    long resolvedAmountMinor,

    @Schema(description = "ISO 4217 currency code from tenant billing profile")
    String currency,

    @Schema(description = "Discount applied in minor units; null when no promo was provided")
    Long discountAmountMinor,

    @Schema(description = "PPM plan version UUID locked at checkout for grandfathering")
    UUID ppmPlanVersionId
) {}
