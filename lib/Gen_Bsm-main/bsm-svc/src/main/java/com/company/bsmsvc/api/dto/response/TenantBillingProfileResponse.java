package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Tenant billing profile response")
public record TenantBillingProfileResponse(
    UUID id,
    UUID tenantId,
    PaymentProvider paymentProvider,
    String externalCustomerId,
    String currency,
    Instant createdAt,
    Instant updatedAt
) {}
