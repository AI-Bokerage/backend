package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import java.time.Instant;
import java.util.UUID;

public record ReferralProgramResponse(

    UUID                   id,
    String                 name,
    String                 description,
    UUID                   referrerRewardPromotionId,
    UUID                   referredRewardPromotionId,
    ReferralProgramStatus  status,
    Integer                maxReferralsPerReferrer,
    Instant                createdAt,
    Instant                updatedAt
) {}
