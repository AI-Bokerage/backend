package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.addon.model.AddOnType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new purchasable add-on catalog entry.
 *
 * <p>{@code code} and {@code name} are required. {@code description} is optional.
 * {@code active} is optional — defaults to {@code true} when absent (BR-A4).
 * {@code code} is normalised (stripped and uppercased) at the service layer (BR-A2).
 */
public record CreateAddOnRequest(

    @NotBlank
    String code,

    @NotBlank
    String name,

    String description,

    @NotNull
    AddOnType type,

    Boolean active
) {}
