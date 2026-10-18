package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.MigrationPlan;
import com.company.bsmsvc.infrastructure.persistence.entity.MigrationPlanEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MigrationPlanEntityMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "status",
        expression = "java(com.company.bsmsvc.domain.enums.MigrationPlanStatus.valueOf(entity.getStatus()))")
    @Mapping(target = "items", ignore = true)
    MigrationPlan toDomain(MigrationPlanEntity entity);

    @Mapping(target = "subscription.id", source = "subscriptionId")
    @Mapping(target = "status", expression = "java(domain.getStatus().name())")
    MigrationPlanEntity toEntity(MigrationPlan domain);
}
