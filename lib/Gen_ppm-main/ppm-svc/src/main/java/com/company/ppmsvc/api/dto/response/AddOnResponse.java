package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.addon.model.AddOnType;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of an {@link com.company.ppmsvc.addon.model.AddOn}.
 *
 * <p>Version, actor IDs, and deletedAt are excluded from the API surface.
 */
public record AddOnResponse(

    UUID id,
    String code,
    String name,
    String description,
    AddOnType type,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
