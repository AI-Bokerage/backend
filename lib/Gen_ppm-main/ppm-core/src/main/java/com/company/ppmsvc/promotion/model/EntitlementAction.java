package com.company.ppmsvc.promotion.model;

/**
 * A {@link PromotionAction} that grants an entitlement (free period, module,
 * or add-on) without reducing the base price. Not consumed by {@link
 * com.company.ppmsvc.promotion.usecase.DiscountCalculator} — the pipeline
 * handles these via {@link AppliedEntitlement#from}.
 */
public sealed interface EntitlementAction extends PromotionAction
    permits FreePeriodDiscount, FreeModuleDiscount, FreeAddOnDiscount {
}
