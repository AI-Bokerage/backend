package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import java.time.Instant;
import java.util.UUID;

public record ReferralCodeResponse(

    UUID               id,
    String             code,
    UUID               referralProgramId,
    String             referrerCustomerId,
    ReferralCodeStatus status,
    Instant            createdAt,
    Instant            updatedAt
) {}
