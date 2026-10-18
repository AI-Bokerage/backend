package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.plan.model.PlanVisibility;
import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO representing a subscription plan.
 *
 * <p>Serialized {@code visibility} uses the stable wire value (e.g. {@code "public"})
 * via {@link PlanVisibility}'s {@code @JsonValue} annotation.
 */
public record PlanResponse(

    UUID           id,
    String         code,
    String         slug,
    String         name,
    String         tagline,
    String         description,
    PlanVisibility visibility,
    int            trialDays,
    Boolean        active,
    String         tier,
    Instant        createdAt,
    Instant        updatedAt
) {}
