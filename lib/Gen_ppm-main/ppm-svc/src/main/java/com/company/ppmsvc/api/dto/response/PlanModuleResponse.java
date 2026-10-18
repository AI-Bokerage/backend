package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.module.model.ModuleCode;
import java.util.UUID;

/**
 * API response DTO representing a module assigned to a plan.
 *
 * <p>Contains the module's UUID plus the display fields most useful to API
 * consumers. The plan's UUID is implicit from the request path and is not
 * repeated here.
 *
 * <p>Serialised {@code moduleCode} uses the stable wire value (e.g.
 * {@code "lead_management"}) via {@link ModuleCode}'s {@code @JsonValue}.
 */
public record PlanModuleResponse(

    UUID       moduleId,
    ModuleCode moduleCode,
    String     moduleName,
    Boolean    active

) {}
