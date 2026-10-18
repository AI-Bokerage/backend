package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.promotion.model.AppliedDiscount;
import com.company.ppmsvc.promotion.model.EntitlementAction;
import com.company.ppmsvc.promotion.model.PriceAction;
import java.math.BigDecimal;

/**
 * Pure discount math. No Spring, no DB, no ports.
 *
 * <p>The name stays "discount" deliberately: this function consumes a
 * <em>base price</em> and applies a price-reducing action to it. Only {@link
 * PriceAction} types are accepted — {@link EntitlementAction} types (free
 * period/module/add-on) have no base price to discount and are handled
 * separately by the pipeline via {@link
 * com.company.ppmsvc.promotion.model.AppliedEntitlement#from}, not by this
 * class. Do not rename this to something like "PromotionEngine"; that would
 * overclaim scope this class does not have.
 */
public interface DiscountCalculator {

    /** Applies {@code action} to {@code baseAmount} and returns the result. */
    AppliedDiscount apply(BigDecimal baseAmount, PriceAction action);
}
