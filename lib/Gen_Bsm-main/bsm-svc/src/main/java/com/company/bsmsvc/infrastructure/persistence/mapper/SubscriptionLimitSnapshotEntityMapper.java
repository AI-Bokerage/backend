package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.SubscriptionLimitSnapshot;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionLimitSnapshotEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionLimitSnapshotEntityMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    SubscriptionLimitSnapshot toDomain(SubscriptionLimitSnapshotEntity entity);

    @Mapping(target = "subscription.id", source = "subscriptionId")
    SubscriptionLimitSnapshotEntity toEntity(SubscriptionLimitSnapshot domain);
}
