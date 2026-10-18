package com.company.ppmsvc.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

/**
 * Request body for assigning or replacing modules on a plan.
 *
 * <p>Uses {@link Set} rather than {@link java.util.List} to deduplicate
 * module IDs at the binding layer (BR-4: idempotent input — duplicate IDs
 * in the request are silently collapsed to one assignment).
 */
public record AssignModulesRequest(

    @NotEmpty(message = "At least one module ID must be provided.")
    Set<UUID> moduleIds

) {}
