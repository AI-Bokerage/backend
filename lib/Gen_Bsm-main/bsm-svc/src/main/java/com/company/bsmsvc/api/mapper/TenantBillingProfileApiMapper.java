package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.TenantBillingProfileResponse;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import org.springframework.stereotype.Component;

@Component
public class TenantBillingProfileApiMapper {

    public TenantBillingProfileResponse toResponse(TenantBillingProfile profile) {
        return new TenantBillingProfileResponse(
            profile.getId(),
            profile.getTenantId(),
            profile.getPaymentProvider(),
            profile.getExternalCustomerId(),
            profile.getCurrency(),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }
}
