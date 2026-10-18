package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.CouponResponse;
import com.company.ppmsvc.coupon.model.Coupon;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the {@link Coupon} domain model and {@link
 * CouponResponse} API DTO. All fields map by name.
 */
@Mapper(componentModel = "spring")
public interface CouponApiMapper {

    CouponResponse toResponse(Coupon coupon);

    List<CouponResponse> toResponseList(List<Coupon> coupons);
}
