package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.promotion.model.ConditionEvaluationResult;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.EligibilityCondition;
import com.company.ppmsvc.promotion.model.EligibilityType;
import com.company.ppmsvc.promotion.model.PlanRestrictionCondition;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionApplicationReason;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.company.ppmsvc.promotion.model.UsageLimitCondition;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Pure implementation of {@link ConditionEvaluator}. Short-circuits on the
 * first failing condition.
 *
 * <p>The per-user usage cap is enforced unconditionally whenever {@link
 * Promotion#getUsageCapPerUser()} is set — it does not require a {@link
 * UsageLimitCondition} entry in the conditions list. That record type is
 * informational only (documents the limit alongside the other conditions);
 * enforcement always reads the {@code Promotion} field directly, so a
 * promotion with {@code usageCapPerUser} set but no explicit {@code
 * UsageLimitCondition} is still capped correctly.
 */
@Service
public class ConditionEvaluatorImpl implements ConditionEvaluator {

    @Override
    public ConditionEvaluationResult evaluate(Promotion promotion, UUID planId, CustomerContext customer,
                                              int currentUsageCount) {
        for (PromotionCondition condition : promotion.getConditions()) {
            ConditionEvaluationResult result = evaluateOne(condition, planId, customer);
            if (!result.pass()) {
                return result;
            }
        }

        Integer cap = promotion.getUsageCapPerUser();
        if (cap != null && currentUsageCount >= cap) {
            return ConditionEvaluationResult.fail(PromotionApplicationReason.USAGE_LIMIT_PER_USER);
        }

        return ConditionEvaluationResult.PASS;
    }

    private static ConditionEvaluationResult evaluateOne(PromotionCondition condition, UUID planId,
                                                          CustomerContext customer) {
        return switch (condition) {
            case PlanRestrictionCondition prc -> {
                if (!prc.planIds().isEmpty() && !prc.planIds().contains(planId)) {
                    yield ConditionEvaluationResult.fail(PromotionApplicationReason.PLAN_NOT_ELIGIBLE);
                }
                yield ConditionEvaluationResult.PASS;
            }
            case EligibilityCondition ec -> {
                boolean violated = (ec.type() == EligibilityType.NEW_CUSTOMER && !customer.isNewCustomer())
                    || (ec.type() == EligibilityType.EXISTING_CUSTOMER && customer.isNewCustomer());
                yield violated
                    ? ConditionEvaluationResult.fail(PromotionApplicationReason.ELIGIBILITY_VIOLATION)
                    : ConditionEvaluationResult.PASS;
            }
            // UsageLimitCondition is informational only — enforcement happens
            // unconditionally above via Promotion.usageCapPerUser.
            case UsageLimitCondition ignored -> ConditionEvaluationResult.PASS;
        };
    }
}
