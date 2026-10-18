package com.company.ppmsvc.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

/**
 * Request body for assigning or replacing add-on availability on a plan.
 *
 * <p>Uses {@link Set} rather than {@link java.util.List} to deduplicate
 * add-on IDs at the binding layer (BR-P4: idempotent input — duplicate IDs
 * in the request are silently collapsed to one assignment).
 */
public record AssignAddOnsRequest(

    @NotEmpty(message = "At least one add-on ID must be provided.")
    Set<UUID> addOnIds
) {}
