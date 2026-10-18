package com.company.ppmdemo.web.dto;

import com.company.ppmsvc.promocode.model.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePromoCodeRequest(
    @NotBlank String code,
    @NotNull DiscountType discountType,
    @NotNull BigDecimal value,
    @NotNull LocalDate validFrom,
    @NotNull LocalDate validUntil
) {}
