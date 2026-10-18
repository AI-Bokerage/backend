package com.company.ppmsvc.plan.model;

import com.company.ppmsvc.module.model.ModuleCode;
import java.util.UUID;

/**
 * Catalog-facing read model for a platform module.
 *
 * <p>Used by {@link com.company.ppmsvc.plan.usecase.CatalogQueryService}
 * and surfaced through Phase-2 REST endpoints.
 */
public record CatalogModuleResponse(
        UUID moduleId,
        ModuleCode code,
        String name,
        String description
) {}
