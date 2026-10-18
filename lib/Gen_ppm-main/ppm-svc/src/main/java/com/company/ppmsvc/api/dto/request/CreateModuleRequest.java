package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.module.model.ModuleCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new platform capability module.
 *
 * <p>{@code code} and {@code name} are required.
 * {@code description} is optional.
 * {@code active} is optional — defaults to {@code true} when absent.
 */
public record CreateModuleRequest(

    @NotNull
    ModuleCode code,

    @NotBlank
    String name,

    String description,

    Boolean active
) {}
