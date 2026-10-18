package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.plan.model.PlanVisibility;

/**
 * Request DTO for updating an existing subscription plan.
 *
 * <p>All fields are optional (partial update / PATCH semantics).
 * A {@code null} value means "leave unchanged".
 * If {@code name} is supplied it must not be blank — validated in the service layer.
 *
 * <p>{@code code} and {@code slug} are intentionally absent: both are immutable
 * after creation and can never be changed via an update.
 */
public record UpdatePlanRequest(

    String         name,
    String         tagline,
    String         description,
    PlanVisibility visibility,
    Integer        trialDays,
    Boolean        active,
    String         tier
) {}
