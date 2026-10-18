package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Documents a promotion's per-user usage cap as a condition entry.
 *
 * <p>This record is informational only — the actual enforcement reads {@link
 * Promotion#getUsageCapPerUser()} plus the redemption ledger (see {@link
 * com.company.ppmsvc.promotion.usecase.ConditionEvaluator}). It exists so the
 * conditions list can display/document the limit alongside the other
 * conditions rather than only in a separate field.
 */
@JsonTypeName("usage_limit")
public record UsageLimitCondition(Integer usageCapPerUser) implements PromotionCondition {
}
