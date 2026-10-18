package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.PromoCodeResponse;
import com.company.ppmsvc.promocode.model.PromoCode;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link PromoCode} domain model and
 * {@link PromoCodeResponse} API DTO.
 *
 * <p>The domain model stores the discount type as {@code discountType}; the
 * response DTO exposes it as {@code type}.  All other fields map by name.
 * Fields absent from the response ({@code version}, {@code createdBy},
 * {@code updatedBy}, {@code deletedAt}) are silently ignored by MapStruct.
 */
@Mapper(componentModel = "spring")
public interface PromoCodeApiMapper {

    /** Converts a domain {@link PromoCode} to a {@link PromoCodeResponse}. */
    @Mapping(source = "discountType", target = "type")
    PromoCodeResponse toResponse(PromoCode promoCode);

    /** Bulk conversion — used by list operations. */
    List<PromoCodeResponse> toResponseList(List<PromoCode> promoCodes);
}
