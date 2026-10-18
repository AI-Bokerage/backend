package com.company.ppmsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/ppm/promo-codes/validate}.
 *
 * <p>{@code code} is matched case-sensitively against the stored value.
 * Callers must pass the normalised (uppercase, stripped) code — the same
 * form returned by the Promo Code Catalog endpoints.
 */
public record ValidatePromoCodeRequest(

    @NotBlank
    String code,

    @NotNull
    UUID planId
) {}
