package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionHistoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionHistoryEntityMapper {

    @Mapping(target = "action", expression = "java(com.company.bsmsvc.domain.enums.SubscriptionHistoryAction.valueOf(entity.getAction()))")
    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "actorType", expression = "java(entity.getActorType() == null ? null : com.company.bsmsvc.domain.enums.ActorType.valueOf(entity.getActorType()))")
    SubscriptionHistory toDomain(SubscriptionHistoryEntity entity);

    @Mapping(target = "action", expression = "java(domain.getAction().name())")
    @Mapping(target = "subscription.id", source = "subscriptionId")
    @Mapping(target = "actorType", expression = "java(domain.getActorType() == null ? null : domain.getActorType().name())")
    SubscriptionHistoryEntity toEntity(SubscriptionHistory domain);
}
