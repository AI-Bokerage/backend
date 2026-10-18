package com.company.bsmsvc.api.dto.response;

import java.util.UUID;
import lombok.Builder;

@Builder
public record AddOnPurchaseResponse(
    UUID   subscriptionAddOnId,
    UUID   invoiceId,
    String checkoutUrl,
    String sessionId,
    UUID   ppmAddOnId,
    UUID   ppmAddOnPriceId,
    Long   ppmResolvedPriceMinor,
    String currency
) {}
