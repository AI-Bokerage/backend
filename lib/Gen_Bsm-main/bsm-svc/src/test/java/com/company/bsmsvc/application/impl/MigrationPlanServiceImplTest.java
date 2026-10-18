package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.domain.enums.MigrationAction;
import com.company.bsmsvc.domain.enums.MigrationPlanStatus;
import com.company.bsmsvc.domain.enums.MigrationResourceType;
import com.company.bsmsvc.domain.model.MigrationPlan;
import com.company.bsmsvc.domain.model.MigrationPlanFilter;
import com.company.bsmsvc.domain.model.MigrationPlanItem;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.MigrationPlanRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MigrationPlanServiceImplTest {

    @Mock
    private MigrationPlanRepositoryPort migrationPlanRepositoryPort;
    @Mock
    private SubscriptionRepositoryPort subscriptionRepositoryPort;
    @Mock
    private PlanVersionMetaPort ppmVersionMetaClient;
    @Mock
    private SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    @Mock
    private SubscriptionEventRepositoryPort subscriptionEventRepositoryPort;
    @Mock
    private TenantOwnershipValidator tenantOwnershipValidator;

    @Spy
    private SubscriptionLifecycleMapper subscriptionLifecycleMapper = new SubscriptionLifecycleMapper();

    @Mock private TenantScopePort tenantScopeEnforcer;

    @InjectMocks
    private MigrationPlanServiceImpl migrationPlanService;

    @BeforeEach
    void setupEnforcer() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createMigrationPlanShouldPersistPlanItemsAndAuditEntries() {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID targetPlanVersionId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        Subscription subscription = Subscription.builder()
            .id(subscriptionId)
            .tenantId(tenantId)
            .planVersionId(UUID.randomUUID())
            .build();

        MigrationPlan draft = MigrationPlan.builder()
            .subscriptionId(subscriptionId)
            .tenantId(tenantId)
            .targetPlanVersionId(targetPlanVersionId)
            .createdBy(createdBy)
            .build();
        MigrationPlanItem draftItem = MigrationPlanItem.builder()
            .resourceType(MigrationResourceType.PROJECT)
            .resourceId(UUID.randomUUID())
            .action(MigrationAction.ARCHIVE)
            .build();

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(ppmVersionMetaClient.getVersionMeta(targetPlanVersionId)).thenReturn(
            new PpmVersionMetaResult(targetPlanVersionId, UUID.randomUUID(), "PRO", 1,
                true, true, LocalDate.of(2026, 1, 1), null, null));
        when(migrationPlanRepositoryPort.savePlan(any(MigrationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(migrationPlanRepositoryPort.saveItem(any(MigrationPlanItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MigrationPlan result = migrationPlanService.createMigrationPlan(draft, List.of(draftItem));

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MigrationPlanStatus.DRAFT);
        assertThat(result.getItems()).hasSize(1);
        verify(subscriptionHistoryRepositoryPort).save(any());
        verify(subscriptionEventRepositoryPort).save(any());
    }

    @Test
    void getMigrationPlanShouldHydrateItems() {
        UUID planId = UUID.randomUUID();
        MigrationPlan plan = MigrationPlan.builder()
            .id(planId)
            .subscriptionId(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .targetPlanVersionId(UUID.randomUUID())
            .status(MigrationPlanStatus.DRAFT)
            .build();
        MigrationPlanItem item = MigrationPlanItem.builder()
            .id(UUID.randomUUID())
            .migrationPlanId(planId)
            .resourceType(MigrationResourceType.USER)
            .resourceId(UUID.randomUUID())
            .action(MigrationAction.FREEZE)
            .createdAt(Instant.now())
            .build();

        when(migrationPlanRepositoryPort.findById(planId)).thenReturn(Optional.of(plan));
        when(migrationPlanRepositoryPort.findItemsByMigrationPlanId(planId)).thenReturn(List.of(item));

        MigrationPlan result = migrationPlanService.getMigrationPlan(planId);

        assertThat(result.getItems()).containsExactly(item);
    }

    @Test
    void listMigrationPlansShouldHydrateItemsForEachPlan() {
        UUID planId = UUID.randomUUID();
        MigrationPlan plan = MigrationPlan.builder()
            .id(planId)
            .subscriptionId(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .targetPlanVersionId(UUID.randomUUID())
            .status(MigrationPlanStatus.DRAFT)
            .build();
        MigrationPlanItem item = MigrationPlanItem.builder()
            .id(UUID.randomUUID())
            .migrationPlanId(planId)
            .resourceType(MigrationResourceType.PROJECT)
            .resourceId(UUID.randomUUID())
            .action(MigrationAction.ARCHIVE)
            .createdAt(Instant.now())
            .build();

        when(migrationPlanRepositoryPort.findPlans(any(MigrationPlanFilter.class), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn(new PageResult<>(List.of(plan), 0, 20, 1, 1, false));
        when(migrationPlanRepositoryPort.findItemsByMigrationPlanId(planId)).thenReturn(List.of(item));

        PageResult<MigrationPlan> result = migrationPlanService.listMigrationPlans(
            new MigrationPlanFilter(null, null, null, null, null, null),
            0,
            20,
            "createdAt",
            "desc"
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().getItems()).containsExactly(item);
    }
}
