package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.PlanResponse;
import com.company.ppmsvc.plan.model.Plan;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the {@link Plan} domain model and
 * {@link PlanResponse} API DTO.
 *
 * <p>All response fields map 1-to-1 by name ({@code id}, {@code code},
 * {@code slug}, {@code name}, {@code tagline}, {@code description},
 * {@code visibility}, {@code trialDays}, {@code active}, {@code createdAt},
 * {@code updatedAt}).  Fields on {@code Plan} that are absent from the
 * response ({@code version}, {@code createdBy}, {@code updatedBy}) are
 * silently ignored by MapStruct.
 *
 * <p>Request → domain construction is handled by
 * {@link com.company.ppmsvc.plan.usecase.PlanApplicationServiceImpl}
 * directly, since domain objects require identity and audit fields that
 * only the service can supply.
 */
@Mapper(componentModel = "spring")
public interface PlanApiMapper {

    /** Converts a domain {@link Plan} to a {@link PlanResponse}. */
    PlanResponse toResponse(Plan plan);

    /** Bulk conversion — used by list endpoints. */
    List<PlanResponse> toResponseList(List<Plan> plans);
}
