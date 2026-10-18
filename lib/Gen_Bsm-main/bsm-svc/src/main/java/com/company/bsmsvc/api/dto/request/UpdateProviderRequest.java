package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Update payment provider request")
public record UpdateProviderRequest(
    @NotNull @Schema(description = "New payment provider") PaymentProvider paymentProvider
) {}
