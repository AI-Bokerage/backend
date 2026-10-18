package com.company.ppmsvc.promotion.model;

/**
 * Outcome of evaluating all of a promotion's conditions via {@link
 * com.company.ppmsvc.promotion.usecase.ConditionEvaluator}.
 *
 * <p>{@code pass=true} means every condition passed; {@code pass=false}
 * carries the first failure's reason and {@code failReason} is non-null.
 */
public record ConditionEvaluationResult(boolean pass, PromotionApplicationReason failReason) {

    public static final ConditionEvaluationResult PASS = new ConditionEvaluationResult(true, null);

    public static ConditionEvaluationResult fail(PromotionApplicationReason reason) {
        return new ConditionEvaluationResult(false, reason);
    }
}
