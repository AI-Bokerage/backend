package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.promotion.model.ReferralEventStatus;
import java.time.Instant;
import java.util.UUID;

public record ReferralEventResponse(

    UUID                id,
    UUID                referralCodeId,
    String              referredCustomerId,
    ReferralEventStatus status,
    Instant             convertedAt,
    Instant             rewardGrantedAt
) {}
