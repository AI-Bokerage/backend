package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import java.util.UUID;

public record UpdateReferralProgramRequest(

    String                 name,
    String                 description,
    UUID                   referrerRewardPromotionId,
    UUID                   referredRewardPromotionId,
    ReferralProgramStatus  status,
    Integer                maxReferralsPerReferrer
) {}
