package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO representing an entitlement catalog entry.
 *
 * <p>Serialised {@code type} uses the stable wire value (e.g. {@code "quota"})
 * via {@link EntitlementType}'s {@code @JsonValue} annotation.
 */
public record EntitlementResponse(

    UUID            id,
    String          code,
    String          name,
    String          description,
    EntitlementType type,
    boolean         active,
    Instant         createdAt,
    Instant         updatedAt
) {}
