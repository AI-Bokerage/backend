package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.DowngradePreflightResponse;
import com.company.bsmsvc.api.dto.response.DowngradeWarningDto;
import com.company.bsmsvc.api.dto.response.ProrationPreviewResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionLimitSnapshotResponse;
import com.company.bsmsvc.domain.model.DowngradeImpact;
import com.company.bsmsvc.domain.model.DowngradeWarning;
import com.company.bsmsvc.domain.model.ProrationPreview;
import com.company.bsmsvc.domain.model.SubscriptionLimitSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CommercialEngineApiMapper {

    @Mapping(target = "delta", source = "proratedAmountMinor")
    ProrationPreviewResponse toResponse(ProrationPreview preview);

    @Mapping(target = "usersOverLimit", source = "details.usersOverLimit")
    @Mapping(target = "projectsOverLimit", source = "details.projectsOverLimit")
    @Mapping(target = "storageOverLimitBytes", source = "details.storageOverLimitBytes")
    @Mapping(target = "featuresLost", source = "details.featuresLost")
    DowngradePreflightResponse toResponse(DowngradeImpact impact);

    DowngradeWarningDto toResponse(DowngradeWarning warning);

    SubscriptionLimitSnapshotResponse toResponse(SubscriptionLimitSnapshot snapshot);
}
