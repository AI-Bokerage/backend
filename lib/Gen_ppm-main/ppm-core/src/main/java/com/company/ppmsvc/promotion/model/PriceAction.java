package com.company.ppmsvc.promotion.model;

/**
 * A {@link PromotionAction} that reduces a base price. Consumed by {@link
 * com.company.ppmsvc.promotion.usecase.DiscountCalculator}.
 *
 * <p>No {@code @JsonTypeInfo} here — the discriminator lives on {@link
 * PromotionAction} and resolves to concrete types via each record's {@code
 * @JsonTypeName}, unaffected by this intermediate interface.
 */
public sealed interface PriceAction extends PromotionAction
    permits PercentageDiscount, FlatDiscount, FixedPriceDiscount {
}
