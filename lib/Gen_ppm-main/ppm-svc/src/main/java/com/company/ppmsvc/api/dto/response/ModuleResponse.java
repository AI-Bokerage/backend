package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.module.model.ModuleCode;
import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO representing a platform capability module.
 *
 * <p>Serialised {@code code} uses the stable wire value (e.g. {@code "lead_management"})
 * via {@link ModuleCode}'s {@code @JsonValue} annotation.
 */
public record ModuleResponse(

    UUID       id,
    ModuleCode code,
    String     name,
    String     description,
    Boolean    active,
    Instant    createdAt,
    Instant    updatedAt
) {}
