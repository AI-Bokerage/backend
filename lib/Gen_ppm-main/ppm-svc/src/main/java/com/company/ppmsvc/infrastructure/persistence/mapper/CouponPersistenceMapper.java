package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.infrastructure.persistence.entity.CouponEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the {@link Coupon} domain model and {@link
 * CouponEntity} JPA entity. Inherited fields from {@code JpaBaseEntity} map
 * automatically.
 */
@Mapper(componentModel = "spring")
public interface CouponPersistenceMapper {

    Coupon toDomain(CouponEntity entity);

    @Mapping(target = "deletedAt", ignore = true)
    CouponEntity toEntity(Coupon coupon);

    List<Coupon> toDomainList(List<CouponEntity> entities);
}
