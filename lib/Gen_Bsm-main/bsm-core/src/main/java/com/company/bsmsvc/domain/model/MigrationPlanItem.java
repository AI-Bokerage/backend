package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.MigrationAction;
import com.company.bsmsvc.domain.enums.MigrationResourceType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MigrationPlanItem {

    private UUID id;
    private UUID migrationPlanId;
    private MigrationResourceType resourceType;
    private UUID resourceId;
    private MigrationAction action;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
