package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.ModuleResponse;
import com.company.ppmsvc.module.model.Module;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the {@link Module} domain model and
 * {@link ModuleResponse} API DTO.
 *
 * <p>All response fields map 1-to-1 by name ({@code id}, {@code code},
 * {@code name}, {@code description}, {@code active}, {@code createdAt},
 * {@code updatedAt}).  Fields on {@code Module} that are absent from the
 * response ({@code version}, {@code createdBy}, {@code updatedBy}) are
 * silently ignored by MapStruct.
 *
 * <p>Request → domain construction is handled by
 * {@link com.company.ppmsvc.module.usecase.ModuleApplicationServiceImpl}
 * directly, since domain objects require identity and audit fields that
 * only the service can supply.
 */
@Mapper(componentModel = "spring")
public interface ModuleApiMapper {

    /** Converts a domain {@link Module} to a {@link ModuleResponse}. */
    ModuleResponse toResponse(Module module);

    /** Bulk conversion — used by list endpoints. */
    List<ModuleResponse> toResponseList(List<Module> modules);
}
