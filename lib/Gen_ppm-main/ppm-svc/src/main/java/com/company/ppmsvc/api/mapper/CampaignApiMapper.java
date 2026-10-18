package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.CampaignResponse;
import com.company.ppmsvc.campaign.model.Campaign;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CampaignApiMapper {

    CampaignResponse toResponse(Campaign campaign);

    List<CampaignResponse> toResponseList(List<Campaign> campaigns);
}
