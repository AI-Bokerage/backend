package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.UUID;

/**
 * Grants free access to a specific add-on for N months. {@code addOnId}
 * must reference an existing AddOn in the catalog.
 */
@JsonTypeName("free_addon")
public record FreeAddOnDiscount(UUID addOnId, Integer durationMonths) implements EntitlementAction {
}
