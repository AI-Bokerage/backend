package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.AddOnPurchaseResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionAddOnResponse;
import com.company.bsmsvc.domain.model.AddOnPurchaseResult;
import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionAddOnApiMapper {

    SubscriptionAddOnResponse toResponse(SubscriptionAddOn addOn);

    AddOnPurchaseResponse toResponse(AddOnPurchaseResult result);
}
