package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.AddOnResponse;
import com.company.ppmsvc.addon.model.AddOn;
import org.mapstruct.Mapper;
import java.util.List;

/**
 * MapStruct mapper from {@link AddOn} domain model to {@link AddOnResponse} DTO.
 *
 * <p>{@link AddOnResponse} is a record (canonical constructor target), so MapStruct
 * maps by matching source getter property names to constructor parameter names.
 * No {@code addOnId} field appears in either type, so the MapStruct 1.6.x builder
 * adder-method bug does not apply here.
 */
@Mapper(componentModel = "spring")
public interface AddOnApiMapper {

    AddOnResponse toResponse(AddOn addOn);

    List<AddOnResponse> toResponseList(List<AddOn> addOns);
}
