package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.MigrationPlan;
import com.company.bsmsvc.domain.model.MigrationPlanFilter;
import com.company.bsmsvc.domain.model.MigrationPlanItem;
import com.company.bsmsvc.domain.model.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for tenant migration plans and their line items (bulk resource
 * migration tracking). Implementations must be thread-safe/stateless.
 */
public interface MigrationPlanRepositoryPort {

    MigrationPlan savePlan(MigrationPlan plan);

    MigrationPlanItem saveItem(MigrationPlanItem item);

    Optional<MigrationPlan> findById(UUID id);

    List<MigrationPlanItem> findItemsByMigrationPlanId(UUID migrationPlanId);

    PageResult<MigrationPlan> findPlans(MigrationPlanFilter filter, int page, int size, String sortBy, String sortDirection);
}
