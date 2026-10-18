package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.promocode.model.PromoValidationReason;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response from the Promo Validation Engine (PPM-08).
 *
 * <p>Always returned with HTTP 200. {@code valid=true} means the code may be
 * applied to the requested plan; {@code valid=false} means it cannot, and
 * {@code reason} explains why.
 *
 * <p>Discount fields ({@code discountType}, {@code discountValue},
 * {@code promoCodeId}, {@code validUntil}) are {@code null} on invalid responses
 * and are excluded from the JSON output via {@code @JsonInclude(NON_NULL)}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromoValidationResponse(

    boolean valid,

    String code,

    UUID promoCodeId,

    DiscountType discountType,

    BigDecimal discountValue,

    PromoValidationReason reason,

    LocalDate validUntil,

    boolean firstTimeOnly
) {}
