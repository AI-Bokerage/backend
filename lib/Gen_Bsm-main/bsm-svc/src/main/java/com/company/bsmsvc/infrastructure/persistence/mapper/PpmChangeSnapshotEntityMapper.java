package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.PpmChangeSnapshot;
import com.company.bsmsvc.infrastructure.persistence.entity.PpmChangeSnapshotEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PpmChangeSnapshotEntityMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "changeType",
        expression = "java(com.company.bsmsvc.domain.enums.PpmPlanChangeType.valueOf(entity.getChangeType()))")
    PpmChangeSnapshot toDomain(PpmChangeSnapshotEntity entity);

    @Mapping(target = "subscription.id", source = "subscriptionId")
    @Mapping(target = "changeType", expression = "java(domain.getChangeType().name())")
    PpmChangeSnapshotEntity toEntity(PpmChangeSnapshot domain);
}
