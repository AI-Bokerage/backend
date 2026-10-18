package com.company.ppmdemo.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Demo request DTO — deliberately minimal, not a copy of ppm-svc's own DTOs. */
public record CreatePlanRequest(
    @NotBlank String code,
    @NotBlank String name,
    Integer trialDays
) {}
