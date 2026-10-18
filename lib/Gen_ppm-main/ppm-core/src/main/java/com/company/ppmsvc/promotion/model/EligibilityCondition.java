package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Restricts a promotion to customers matching {@code type} — new customers
 * only, or existing customers only.
 *
 * <p>The wire key for {@code type} is renamed to {@code eligibilityType} via
 * {@code @JsonProperty} — {@link PromotionCondition}'s {@code @JsonTypeInfo}
 * discriminator property is also named {@code "type"}, and without this
 * rename the two would collide on the same JSON key, corrupting the
 * discriminator on serialization. The Java accessor stays {@link #type()}.
 */
@JsonTypeName("eligibility")
public record EligibilityCondition(@JsonProperty("eligibilityType") EligibilityType type) implements PromotionCondition {
}
