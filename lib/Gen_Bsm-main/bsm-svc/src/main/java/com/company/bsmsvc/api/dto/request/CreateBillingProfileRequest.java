package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

@Schema(description = "Create tenant billing profile request")
public record CreateBillingProfileRequest(
    @NotNull @Schema(description = "Tenant ID") UUID tenantId,
    @NotNull @Schema(description = "Payment provider to use") PaymentProvider paymentProvider,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code (e.g. INR, USD, EUR)")
    @Schema(description = "Billing currency (ISO 4217, e.g. INR, USD, EUR)", example = "INR")
    String currency
) {}
