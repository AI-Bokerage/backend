package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.request.CreateMigrationPlanItemRequest;
import com.company.bsmsvc.api.dto.request.CreateMigrationPlanRequest;
import com.company.bsmsvc.api.dto.response.MigrationPlanItemResponse;
import com.company.bsmsvc.api.dto.response.MigrationPlanResponse;
import com.company.bsmsvc.domain.model.MigrationPlan;
import com.company.bsmsvc.domain.model.MigrationPlanItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MigrationPlanApiMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "items", ignore = true)
    MigrationPlan toDomain(CreateMigrationPlanRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "migrationPlanId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    MigrationPlanItem toDomain(CreateMigrationPlanItemRequest request);

    List<MigrationPlanItem> toDomainItems(List<CreateMigrationPlanItemRequest> requests);

    MigrationPlanResponse toResponse(MigrationPlan plan);

    MigrationPlanItemResponse toResponse(MigrationPlanItem item);
}
