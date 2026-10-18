package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.PlanModuleResponse;
import com.company.ppmsvc.module.model.Module;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link Module} domain model and
 * {@link PlanModuleResponse} API DTO.
 *
 * <p>The response carries module identity and display fields; the plan UUID
 * is implicit from the request path. Field renames:
 * <ul>
 *   <li>{@code Module.id}   → {@code PlanModuleResponse.moduleId}</li>
 *   <li>{@code Module.code} → {@code PlanModuleResponse.moduleCode}</li>
 *   <li>{@code Module.name} → {@code PlanModuleResponse.moduleName}</li>
 *   <li>{@code Module.active} → {@code PlanModuleResponse.active} (by name)</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface PlanModuleApiMapper {

    @Mapping(source = "id",   target = "moduleId")
    @Mapping(source = "code", target = "moduleCode")
    @Mapping(source = "name", target = "moduleName")
    PlanModuleResponse toResponse(Module module);
}
