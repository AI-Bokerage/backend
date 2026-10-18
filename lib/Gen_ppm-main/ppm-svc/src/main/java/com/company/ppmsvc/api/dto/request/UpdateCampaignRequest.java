package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.campaign.model.CampaignStatus;
import java.time.LocalDate;

public record UpdateCampaignRequest(

    String         name,
    String         description,
    LocalDate      validFrom,
    LocalDate      validUntil,
    CampaignStatus status
) {}
