package com.company.ppmsvc.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReferralRewardResponse(

    UUID   promotionId,

    String customerId,

    String couponCode
) {}
