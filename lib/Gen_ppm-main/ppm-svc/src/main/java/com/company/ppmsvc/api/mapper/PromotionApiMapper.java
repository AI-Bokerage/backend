package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.PromotionResponse;
import com.company.ppmsvc.promotion.model.Promotion;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the {@link Promotion} domain model and {@link
 * PromotionResponse} API DTO. All fields map by name.
 */
@Mapper(componentModel = "spring")
public interface PromotionApiMapper {

    PromotionResponse toResponse(Promotion promotion);

    List<PromotionResponse> toResponseList(List<Promotion> promotions);
}
