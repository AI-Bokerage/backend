package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionScheduleEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionScheduleEntityMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "actionType", expression = "java(com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType.valueOf(entity.getActionType()))")
    @Mapping(target = "status", expression = "java(com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus.valueOf(entity.getStatus()))")
    SubscriptionSchedule toDomain(SubscriptionScheduleEntity entity);

    default SubscriptionScheduleEntity toEntity(SubscriptionSchedule domain) {
        SubscriptionScheduleEntity entity = new SubscriptionScheduleEntity();
        entity.setId(domain.getId());
        entity.setSubscription(SubscriptionEntity.builder().id(domain.getSubscriptionId()).build());
        entity.setTenantId(domain.getTenantId());
        entity.setActionType(SubscriptionScheduleActionType.valueOf(domain.getActionType().name()).name());
        entity.setTargetPlanVersionId(domain.getTargetPlanVersionId());
        entity.setEffectiveAt(domain.getEffectiveAt());
        entity.setStatus(SubscriptionScheduleStatus.valueOf(domain.getStatus().name()).name());
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setExecutedAt(domain.getExecutedAt());
        entity.setExecutedBy(domain.getExecutedBy());
        entity.setFailureReason(domain.getFailureReason());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setVersion(domain.getVersion());
        return entity;
    }
}
