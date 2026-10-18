package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Update billing currency request")
public record UpdateCurrencyRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code (e.g. INR, USD, EUR)")
    @Schema(description = "New billing currency (ISO 4217, e.g. INR, USD, EUR)", example = "EUR")
    String currency
) {}
