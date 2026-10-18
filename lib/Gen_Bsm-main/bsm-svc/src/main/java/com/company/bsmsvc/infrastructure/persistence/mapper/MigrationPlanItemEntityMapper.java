package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.MigrationPlanItem;
import com.company.bsmsvc.infrastructure.persistence.entity.MigrationPlanItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MigrationPlanItemEntityMapper {

    @Mapping(target = "migrationPlanId", source = "migrationPlan.id")
    @Mapping(target = "resourceType",
        expression = "java(com.company.bsmsvc.domain.enums.MigrationResourceType.valueOf(entity.getResourceType()))")
    @Mapping(target = "action",
        expression = "java(com.company.bsmsvc.domain.enums.MigrationAction.valueOf(entity.getAction()))")
    MigrationPlanItem toDomain(MigrationPlanItemEntity entity);

    @Mapping(target = "migrationPlan.id", source = "migrationPlanId")
    @Mapping(target = "resourceType", expression = "java(domain.getResourceType().name())")
    @Mapping(target = "action", expression = "java(domain.getAction().name())")
    MigrationPlanItemEntity toEntity(MigrationPlanItem domain);
}
