package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.promocode.model.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a new promo code.
 *
 * <p>{@code firstTimeOnly} defaults to {@code false} when absent (BR-5).
 * {@code active} defaults to {@code true} when absent (BR-5).
 * {@code usageCap} is optional — {@code null} means unlimited redemptions.
 *
 * <p>The code is normalised to uppercase with leading/trailing whitespace stripped
 * before persistence (BR-2).
 */
public record CreatePromoCodeRequest(

    @NotBlank
    String code,

    @NotNull
    DiscountType type,

    @NotNull
    BigDecimal value,

    @NotNull
    LocalDate validFrom,

    @NotNull
    LocalDate validUntil,

    Integer usageCap,
    Boolean firstTimeOnly,
    Boolean active
) {}
