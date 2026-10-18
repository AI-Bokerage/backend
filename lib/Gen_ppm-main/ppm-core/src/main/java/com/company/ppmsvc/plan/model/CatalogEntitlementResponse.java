package com.company.ppmsvc.plan.model;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import java.util.UUID;

/**
 * Catalog-facing read model for an entitlement assignment.
 *
 * <p>Combines the entitlement definition (code, name, type) with the
 * plan-specific override value stored in {@code ppm_plan_entitlements}.
 */
public record CatalogEntitlementResponse(
        UUID entitlementId,
        String code,
        String name,
        String description,
        EntitlementType type,
        String value
) {}
