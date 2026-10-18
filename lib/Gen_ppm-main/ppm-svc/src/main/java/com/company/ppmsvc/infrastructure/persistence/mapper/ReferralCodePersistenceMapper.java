package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.infrastructure.persistence.entity.ReferralCodeEntity;
import com.company.ppmsvc.referral.model.ReferralCode;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between {@link ReferralCode} and {@link ReferralCodeEntity}. No JSONB — plain interface. */
@Mapper(componentModel = "spring")
public interface ReferralCodePersistenceMapper {

    @Mapping(target = "status", expression = "java(com.company.ppmsvc.promotion.model.ReferralCodeStatus.fromValue(entity.getStatus()))")
    ReferralCode toDomain(ReferralCodeEntity entity);

    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "status", expression = "java(code.getStatus().getValue())")
    ReferralCodeEntity toEntity(ReferralCode code);

    List<ReferralCode> toDomainList(List<ReferralCodeEntity> entities);
}
