package com.company.ppmsvc.plan.model;

import java.util.List;
import java.util.UUID;

/**
 * Full catalog read model for plan detail endpoints.
 *
 * <p>Includes the complete module and entitlement composition for the plan,
 * plus the latest active version snapshot.
 */
public record CatalogPlanDetailResponse(
        UUID planId,
        String code,
        String slug,
        String name,
        String tagline,
        String description,
        PlanVisibility visibility,
        int trialDays,
        boolean active,
        CatalogVersionResponse latestVersion,
        List<CatalogModuleResponse> modules,
        List<CatalogEntitlementResponse> entitlements
) {}
