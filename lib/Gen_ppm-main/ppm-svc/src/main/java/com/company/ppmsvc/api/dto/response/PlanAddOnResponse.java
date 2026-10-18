package com.company.ppmsvc.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of a plan ↔ add-on assignment.
 *
 * <p>The join record is create-only (no update), so only {@code createdAt} is
 * present — there is no {@code updatedAt}.
 */
public record PlanAddOnResponse(

    UUID id,
    UUID planId,
    UUID addOnId,
    Instant createdAt
) {}
