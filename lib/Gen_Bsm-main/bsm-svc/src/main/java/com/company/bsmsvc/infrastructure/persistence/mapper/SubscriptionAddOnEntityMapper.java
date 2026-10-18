package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionAddOnEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionAddOnEntityMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    SubscriptionAddOn toDomain(SubscriptionAddOnEntity entity);

    @Mapping(target = "subscription.id", source = "subscriptionId")
    SubscriptionAddOnEntity toEntity(SubscriptionAddOn domain);
}
