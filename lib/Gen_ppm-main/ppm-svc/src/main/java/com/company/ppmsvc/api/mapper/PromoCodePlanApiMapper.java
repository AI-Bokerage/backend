package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.PromoCodePlanResponse;
import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the {@link PromoCodePlan} domain model and
 * {@link PromoCodePlanResponse} API DTO.
 *
 * <p>All fields map by name — no renames are required.
 */
@Mapper(componentModel = "spring")
public interface PromoCodePlanApiMapper {

    PromoCodePlanResponse toResponse(PromoCodePlan promoCodePlan);

    List<PromoCodePlanResponse> toResponseList(List<PromoCodePlan> promoCodePlans);
}
