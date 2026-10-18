package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Grants free subscription for N months. No price reduction — the base
 * price stands; the customer is simply not charged for the granted period.
 */
@JsonTypeName("free_period")
public record FreePeriodDiscount(Integer durationMonths) implements EntitlementAction {
}
