package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.promotion.model.ConditionEvaluationResult;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.Promotion;
import java.util.UUID;

/**
 * Evaluates a {@link Promotion}'s condition list against a plan and customer.
 * Pure — no ports, no DB. {@code currentUsageCount} is supplied by the caller
 * (read from the redemption ledger) since usage limits are the one stateful
 * condition.
 */
public interface ConditionEvaluator {

    /** Returns the first failing condition's result, or {@link ConditionEvaluationResult#PASS}. */
    ConditionEvaluationResult evaluate(Promotion promotion, UUID planId, CustomerContext customer,
                                       int currentUsageCount);
}
