package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Add payment method request")
public record AddPaymentMethodRequest(
    @NotNull @Schema(description = "Tenant ID") UUID tenantId,
    @NotBlank @Schema(description = "Payment method token from provider SDK (e.g. pm_xxx for Stripe)") String paymentMethodToken,
    @NotNull @Schema(description = "Payment method type") PaymentMethodType type,
    @Schema(description = "Card brand (e.g. visa, mastercard)") String brand,
    @Schema(description = "Last four digits of card") String lastFour,
    @Schema(description = "Card expiry month (1-12)") Integer expMonth,
    @Schema(description = "Card expiry year (4 digits)") Integer expYear,
    @Schema(description = "Set as default payment method", defaultValue = "false") boolean makeDefault,
    @Schema(description = "Customer name (used if customer doesn't exist yet)") String customerName,
    @Schema(description = "Customer email (used if customer doesn't exist yet)") String customerEmail
) {}
