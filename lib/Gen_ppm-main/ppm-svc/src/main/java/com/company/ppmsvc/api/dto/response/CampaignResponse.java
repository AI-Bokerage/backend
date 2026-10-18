package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.campaign.model.CampaignStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CampaignResponse(

    UUID           id,
    String         name,
    String         description,
    CampaignStatus status,
    LocalDate      validFrom,
    LocalDate      validUntil,
    Instant        createdAt,
    Instant        updatedAt
) {}
