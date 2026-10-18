package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The action a {@link Promotion} applies — either a price reduction or an
 * entitlement grant.
 *
 * <p>Phase 0 had a flat hierarchy (just {@code PercentageDiscount}/{@code
 * FlatDiscount}). Phase 4 introduces a two-level sealed hierarchy — {@link
 * PriceAction} and {@link EntitlementAction} — because the rule of three is
 * now met on both sides (three price-reducing types, three entitlement-
 * granting types) and the two categories have genuinely different shared
 * invariants: a {@link PriceAction} consumes a base price and reduces it
 * (via {@link com.company.ppmsvc.promotion.usecase.DiscountCalculator}); an
 * {@link EntitlementAction} grants something without touching the price (via
 * {@link AppliedEntitlement#from}). The compiler enforces this split — {@code
 * DiscountCalculator.apply} only accepts {@link PriceAction}.
 *
 * <p>{@code @JsonTypeInfo} stays on this top-level interface. Jackson
 * resolves {@code "type": "percentage"/"flat"/...} to the concrete record
 * regardless of whether it implements {@code PromotionAction} directly or
 * via an intermediate sealed interface — the discriminator values and {@code
 * @JsonTypeName} annotations are unchanged, so existing {@code
 * action_payload} JSONB rows deserialize exactly as before. No data
 * migration was needed for this restructuring.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
public sealed interface PromotionAction permits PriceAction, EntitlementAction {
}
