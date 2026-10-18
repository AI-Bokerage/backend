package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.EntitlementResponse;
import com.company.ppmsvc.entitlement.model.Entitlement;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the {@link Entitlement} domain model and
 * {@link EntitlementResponse} API DTO.
 *
 * <p>All response fields map 1-to-1 by name ({@code id}, {@code code},
 * {@code name}, {@code description}, {@code type}, {@code active},
 * {@code createdAt}, {@code updatedAt}).  Fields on {@code Entitlement} that
 * are absent from the response ({@code version}, {@code createdBy},
 * {@code updatedBy}) are silently ignored by MapStruct.
 */
@Mapper(componentModel = "spring")
public interface EntitlementApiMapper {

    /** Converts a domain {@link Entitlement} to an {@link EntitlementResponse}. */
    EntitlementResponse toResponse(Entitlement entitlement);

    /** Bulk conversion — used by list operations. */
    List<EntitlementResponse> toResponseList(List<Entitlement> entitlements);
}
