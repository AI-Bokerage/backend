package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.UUID;

/**
 * Grants free access to a specific module for N months. {@code moduleId}
 * must reference an existing Module in the catalog.
 */
@JsonTypeName("free_module")
public record FreeModuleDiscount(UUID moduleId, Integer durationMonths) implements EntitlementAction {
}
