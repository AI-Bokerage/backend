package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.plan.model.PlanVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new subscription plan.
 *
 * <p>{@code code}, {@code name}, and {@code visibility} are required.
 * {@code tagline} and {@code description} are optional.
 * {@code trialDays} defaults to {@code 0} when absent.
 * {@code active} defaults to {@code true} when absent.
 *
 * <p>Slug policy: slugs are system-generated in the form {@code PLN-0001}.
 * Clients must never supply a slug — the service generates one automatically
 * via a dedicated database sequence ({@code ppm_plan_slug_seq}).
 */
public record CreatePlanRequest(

    @NotBlank
    String code,

    @NotBlank
    String name,

    String tagline,

    String description,

    @NotNull
    PlanVisibility visibility,

    Integer trialDays,

    Boolean active,

    String tier
) {}
