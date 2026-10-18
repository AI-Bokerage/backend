package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.ProrationPreview;
import com.company.bsmsvc.infrastructure.persistence.entity.ProrationPreviewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProrationPreviewEntityMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "prorationMode",
        expression = "java(com.company.bsmsvc.domain.enums.ProrationMode.valueOf(entity.getProrationMode()))")
    ProrationPreview toDomain(ProrationPreviewEntity entity);

    @Mapping(target = "subscription.id", source = "subscriptionId")
    @Mapping(target = "prorationMode", expression = "java(domain.getProrationMode().name())")
    ProrationPreviewEntity toEntity(ProrationPreview domain);
}
