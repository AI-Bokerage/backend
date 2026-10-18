package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.promotion.model.AppliedEntitlement;
import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionApplicationReason;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response from {@code POST /api/v1/ppm/quotes}. Always returned with HTTP
 * 200 (only plan/price-not-found returns 404).
 *
 * <p>{@code discountAmount}, {@code promotionId}, and {@code appliedAction}
 * are {@code null} whenever no discount was applied and are excluded from
 * the JSON output via {@code @JsonInclude(NON_NULL)}. {@code conditionsSkipped}
 * is {@code true} when the request omitted {@code customerContext}.
 *
 * <p>{@code grantedEntitlement} is populated instead of {@code
 * discountAmount} when the promotion's action is an entitlement grant (free
 * period/module/add-on) — {@code finalAmount} then equals {@code baseAmount}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PriceQuoteResponse(

    BigDecimal baseAmount,

    String currency,

    BigDecimal discountAmount,

    BigDecimal finalAmount,

    PromotionApplicationReason reason,

    UUID promotionId,

    PromotionAction appliedAction,

    boolean conditionsSkipped,

    AppliedEntitlement grantedEntitlement
) {}
