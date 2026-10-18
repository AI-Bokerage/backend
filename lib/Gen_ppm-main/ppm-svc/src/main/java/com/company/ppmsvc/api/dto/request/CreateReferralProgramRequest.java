package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateReferralProgramRequest(

    @NotBlank
    String name,

    String description,

    @NotNull
    UUID referrerRewardPromotionId,

    @NotNull
    UUID referredRewardPromotionId,

    ReferralProgramStatus status,

    Integer maxReferralsPerReferrer
) {}
