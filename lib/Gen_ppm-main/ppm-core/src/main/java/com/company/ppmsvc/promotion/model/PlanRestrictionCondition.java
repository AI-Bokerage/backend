package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Set;
import java.util.UUID;

/**
 * Restricts a promotion to a specific set of plans.
 *
 * <p>Empty {@code planIds} means unrestricted — applies to all plans.
 */
@JsonTypeName("plan_restriction")
public record PlanRestrictionCondition(Set<UUID> planIds) implements PromotionCondition {
}
