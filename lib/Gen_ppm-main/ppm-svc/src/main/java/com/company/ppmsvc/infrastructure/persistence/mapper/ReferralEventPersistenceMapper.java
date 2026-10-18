package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.infrastructure.persistence.entity.ReferralEventEntity;
import com.company.ppmsvc.referral.model.ReferralEvent;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between {@link ReferralEvent} and {@link ReferralEventEntity}. No JSONB — plain interface. */
@Mapper(componentModel = "spring")
public interface ReferralEventPersistenceMapper {

    @Mapping(target = "status", expression = "java(com.company.ppmsvc.promotion.model.ReferralEventStatus.fromValue(entity.getStatus()))")
    ReferralEvent toDomain(ReferralEventEntity entity);

    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "status", expression = "java(event.getStatus().getValue())")
    ReferralEventEntity toEntity(ReferralEvent event);

    List<ReferralEvent> toDomainList(List<ReferralEventEntity> entities);
}
