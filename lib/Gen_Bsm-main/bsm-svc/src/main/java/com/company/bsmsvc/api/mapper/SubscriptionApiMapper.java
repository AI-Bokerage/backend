package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.SubscriptionResponse;
import com.company.bsmsvc.domain.model.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionApiMapper {

    SubscriptionResponse toResponse(Subscription subscription);
}
