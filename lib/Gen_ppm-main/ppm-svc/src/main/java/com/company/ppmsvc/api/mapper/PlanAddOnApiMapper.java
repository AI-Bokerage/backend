package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.PlanAddOnResponse;
import com.company.ppmsvc.planaddon.model.PlanAddOn;
import org.mapstruct.Mapper;
import java.util.List;

/**
 * MapStruct mapper from {@link PlanAddOn} domain model to {@link PlanAddOnResponse} DTO.
 *
 * <p>{@link PlanAddOnResponse} is a record, so MapStruct uses the canonical constructor
 * rather than a builder. The MapStruct 1.6.x adder-method bug (which misidentifies
 * builder methods named {@code addOnId(UUID)} as collection adders) does NOT apply to
 * record targets — MapStruct resolves constructor parameters by name, not by builder
 * method introspection.
 */
@Mapper(componentModel = "spring")
public interface PlanAddOnApiMapper {

    PlanAddOnResponse toResponse(PlanAddOn planAddOn);

    List<PlanAddOnResponse> toResponseList(List<PlanAddOn> planAddOns);
}
