package com.company.ppmsvc.plan.model;

import java.util.UUID;

/**
 * Lightweight catalog read model for plan list endpoints.
 *
 * <p>Excludes the full module/entitlement detail — use
 * {@link CatalogPlanDetailResponse} when the full composition is needed.
 * {@code latestVersion} is {@code null} when the plan has no version rows.
 */
public record CatalogPlanSummaryResponse(
        UUID planId,
        String code,
        String slug,
        String name,
        String tagline,
        PlanVisibility visibility,
        int trialDays,
        boolean active,
        CatalogVersionResponse latestVersion
) {}
