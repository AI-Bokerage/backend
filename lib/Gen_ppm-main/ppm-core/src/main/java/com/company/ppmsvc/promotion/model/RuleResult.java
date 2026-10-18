package com.company.ppmsvc.promotion.model;

/**
 * Outcome of evaluating a single {@link PromotionCondition}.
 */
public record RuleResult(boolean pass, PromotionApplicationReason reason) {
}
