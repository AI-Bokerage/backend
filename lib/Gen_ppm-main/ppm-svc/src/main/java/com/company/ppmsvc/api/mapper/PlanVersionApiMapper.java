package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.PlanVersionResponse;
import com.company.ppmsvc.plan.model.PlanVersion;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the {@link PlanVersion} domain model and
 * {@link PlanVersionResponse} API DTO.
 */
@Mapper(componentModel = "spring")
public interface PlanVersionApiMapper {

    PlanVersionResponse toResponse(PlanVersion version);

    List<PlanVersionResponse> toResponseList(List<PlanVersion> versions);
}
