package com.company.ppmsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for creating a new coupon.
 *
 * <p>{@code code} is normalised to uppercase with whitespace stripped before
 * persistence. {@code active} defaults to {@code true} when absent.
 */
public record CreateCouponRequest(

    @NotBlank
    String code,

    @NotNull
    UUID promotionId,

    Boolean active
) {}
