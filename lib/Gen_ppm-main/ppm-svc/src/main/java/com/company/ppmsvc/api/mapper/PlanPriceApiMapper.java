package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.PlanPriceResponse;
import com.company.ppmsvc.api.dto.response.ResolvedPriceResponse;
import com.company.ppmsvc.planprice.model.PlanPrice;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link PlanPrice} domain model and
 * {@link PlanPriceResponse} API DTO.
 *
 * <p>All response fields map 1-to-1 by name. Fields on {@link PlanPrice} that
 * are absent from the response ({@code version}, {@code createdBy}, {@code updatedBy})
 * are silently ignored by MapStruct.
 */
@Mapper(componentModel = "spring")
public interface PlanPriceApiMapper {

    /** Converts a domain {@link PlanPrice} to a {@link PlanPriceResponse}. */
    PlanPriceResponse toResponse(PlanPrice price);

    /** Bulk conversion — used by list operations. */
    List<PlanPriceResponse> toResponseList(List<PlanPrice> prices);

    /**
     * Converts a domain {@link PlanPrice} to a {@link ResolvedPriceResponse} for the
     * Pricing Resolver Engine (PPM-09).
     *
     * <p>{@code id} → {@code priceId}; {@code version} passes through from {@link
     * com.company.ppmsvc.common.BaseEntity}.
     */
    @Mapping(source = "id", target = "priceId")
    ResolvedPriceResponse toResolvedPriceResponse(PlanPrice price);
}
