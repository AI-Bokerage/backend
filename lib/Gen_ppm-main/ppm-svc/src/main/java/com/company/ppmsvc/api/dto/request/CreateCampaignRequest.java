package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.campaign.model.CampaignStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateCampaignRequest(

    @NotBlank
    String name,

    String description,

    @NotNull
    LocalDate validFrom,

    @NotNull
    LocalDate validUntil,

    CampaignStatus status
) {}
