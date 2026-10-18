package com.company.ppmdemo.web.dto;

import java.util.UUID;

/** Demo response DTO — the controller maps ppm-core's {@code Plan} domain model to this. */
public record PlanView(
    UUID id,
    String code,
    String slug,
    String name,
    boolean active,
    int trialDays
) {}
