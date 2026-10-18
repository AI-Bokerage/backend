package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A condition that must hold for a {@link Promotion} to apply.
 *
 * <p>Stored as a JSONB array in the {@code conditions_payload} column,
 * separate from {@code action_payload} — the {@code type} discriminator
 * values ("plan_restriction", "eligibility", "usage_limit") do not collide
 * with {@link PromotionAction}'s ("percentage", "flat") because each lives in
 * its own column/collection; there is no shared deserialization context.
 *
 * <p>No intermediate rule-engine framework — just a typed list evaluated
 * sequentially by {@link com.company.ppmsvc.promotion.usecase.ConditionEvaluator}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
public sealed interface PromotionCondition permits
    PlanRestrictionCondition,
    EligibilityCondition,
    UsageLimitCondition {
}
