package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new entitlement catalog entry.
 *
 * <p>{@code code}, {@code name}, and {@code type} are required.
 * {@code description} is optional.
 * {@code active} is optional — defaults to {@code true} when absent.
 */
public record CreateEntitlementRequest(

    @NotBlank
    String code,

    @NotBlank
    String name,

    String description,

    @NotNull
    EntitlementType type,

    Boolean active
) {}
