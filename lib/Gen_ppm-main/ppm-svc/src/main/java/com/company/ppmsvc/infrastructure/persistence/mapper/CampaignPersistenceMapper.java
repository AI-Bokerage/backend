package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.campaign.model.Campaign;
import com.company.ppmsvc.infrastructure.persistence.entity.CampaignEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between {@link Campaign} and {@link CampaignEntity}. No JSONB — plain interface. */
@Mapper(componentModel = "spring")
public interface CampaignPersistenceMapper {

    @Mapping(target = "status", expression = "java(com.company.ppmsvc.campaign.model.CampaignStatus.fromValue(entity.getStatus()))")
    Campaign toDomain(CampaignEntity entity);

    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "status", expression = "java(campaign.getStatus().getValue())")
    CampaignEntity toEntity(Campaign campaign);

    List<Campaign> toDomainList(List<CampaignEntity> entities);
}
