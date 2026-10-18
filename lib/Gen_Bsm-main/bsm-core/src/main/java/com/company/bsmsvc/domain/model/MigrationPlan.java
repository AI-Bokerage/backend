package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.MigrationPlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MigrationPlan {

    private UUID id;
    private UUID subscriptionId;
    private UUID tenantId;
    private UUID targetPlanVersionId;
    private MigrationPlanStatus status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Items are populated on reads; null on plain saves.
     * Use MigrationPlanRepositoryPort.findItemsByMigrationPlanId for queries.
     */
    private List<MigrationPlanItem> items;
}
