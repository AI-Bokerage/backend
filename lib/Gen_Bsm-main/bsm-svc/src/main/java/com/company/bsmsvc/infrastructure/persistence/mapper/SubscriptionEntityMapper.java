package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionEntityMapper {

    @Mapping(target = "domainEvents", ignore = true)
    Subscription toDomain(SubscriptionEntity entity);

    SubscriptionEntity toEntity(Subscription domain);
}
