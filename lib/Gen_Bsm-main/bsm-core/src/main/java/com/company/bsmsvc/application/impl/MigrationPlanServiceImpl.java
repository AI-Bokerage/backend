package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.application.service.MigrationPlanService;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.MigrationPlanStatus;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.event.MigrationPlanCreatedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.MigrationPlanNotFoundException;
import com.company.bsmsvc.domain.exception.PlanNotFoundException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.model.MigrationPlan;
import com.company.bsmsvc.domain.model.MigrationPlanFilter;
import com.company.bsmsvc.domain.model.MigrationPlanItem;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.MigrationPlanRepositoryPort;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationPlanServiceImpl implements MigrationPlanService {

    private final MigrationPlanRepositoryPort migrationPlanRepositoryPort;
    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final PlanVersionMetaPort ppmVersionMetaClient;
    private final SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    private final SubscriptionEventRepositoryPort subscriptionEventRepositoryPort;
    private final SubscriptionLifecycleMapper subscriptionLifecycleMapper;
    private final TenantOwnershipValidator tenantOwnershipValidator;
    private final TenantScopePort tenantScopeEnforcer;

    @Override
    @Transactional
    public MigrationPlan createMigrationPlan(MigrationPlan plan, List<MigrationPlanItem> items) {
        tenantScopeEnforcer.assertTenantAccess(plan.getTenantId());
        Subscription subscription = subscriptionRepositoryPort.findById(plan.getSubscriptionId())
            .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found: " + plan.getSubscriptionId()));
        tenantOwnershipValidator.validate(subscription, plan.getTenantId());

        validateTargetPlanVersion(plan.getTargetPlanVersionId());

        Instant now = Instant.now();
        MigrationPlan toCreate = plan.toBuilder()
            .id(plan.getId() == null ? UUID.randomUUID() : plan.getId())
            .status(plan.getStatus() == null ? MigrationPlanStatus.DRAFT : plan.getStatus())
            .createdAt(now)
            .updatedAt(now)
            .build();
        MigrationPlan savedPlan = migrationPlanRepositoryPort.savePlan(toCreate);

        List<MigrationPlanItem> savedItems = items.stream()
            .map(item -> item.toBuilder()
                .id(item.getId() == null ? UUID.randomUUID() : item.getId())
                .migrationPlanId(savedPlan.getId())
                .createdAt(item.getCreatedAt() == null ? now : item.getCreatedAt())
                .build())
            .map(migrationPlanRepositoryPort::saveItem)
            .toList();

        subscriptionHistoryRepositoryPort.save(subscriptionLifecycleMapper.toHistory(
            subscription,
            SubscriptionHistoryAction.MIGRATION_PLAN_CREATED,
            subscription.getPlanVersionId(),
            savedPlan.getTargetPlanVersionId(),
            "Created migration plan",
            savedPlan.getCreatedBy().toString(),
            savedPlan.getCreatedBy(),
            ActorType.USER,
            now
        ));
        subscriptionEventRepositoryPort.save(subscriptionLifecycleMapper.toEvent(
            subscription,
            SubscriptionEventType.MIGRATION_PLAN_CREATED,
            Map.of(
                "migrationPlanId", savedPlan.getId().toString(),
                "targetPlanVersionId", savedPlan.getTargetPlanVersionId().toString(),
                "itemCount", savedItems.size(),
                "status", savedPlan.getStatus().name()
            ),
            savedPlan.getCreatedBy(),
            ActorType.USER,
            now
        ));

        subscription.registerEvent(new MigrationPlanCreatedEvent(
            savedPlan.getId(),
            subscription.getId(),
            subscription.getTenantId(),
            savedPlan.getTargetPlanVersionId(),
            savedItems.size(),
            now
        ));
        log.debug("[createMigrationPlan] Domain events registered: {}", subscription.pullDomainEvents());
        log.info("[createMigrationPlan] SUCCESS migrationPlanId={} subscriptionId={} itemCount={}",
            savedPlan.getId(), subscription.getId(), savedItems.size());

        return savedPlan.toBuilder().items(savedItems).build();
    }

    @Override
    @Transactional(readOnly = true)
    public MigrationPlan getMigrationPlan(UUID id) {
        MigrationPlan plan = migrationPlanRepositoryPort.findById(id)
            .orElseThrow(() -> new MigrationPlanNotFoundException("Migration plan not found: " + id));
        tenantScopeEnforcer.assertTenantAccess(plan.getTenantId());
        return hydrateItems(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<MigrationPlan> listMigrationPlans(
        MigrationPlanFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDir
    ) {
        MigrationPlanFilter effectiveFilter = new MigrationPlanFilter(
            tenantScopeEnforcer.resolveEffectiveTenantId(filter.tenantId()),
            filter.subscriptionId(), filter.targetPlanVersionId(), filter.status(),
            filter.dateFrom(), filter.dateTo()
        );
        PageResult<MigrationPlan> result = migrationPlanRepositoryPort.findPlans(effectiveFilter, page, size, sortBy, sortDir);
        List<MigrationPlan> hydrated = result.content().stream()
            .map(this::hydrateItems)
            .toList();
        return new PageResult<>(hydrated, result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasNext());
    }

    private MigrationPlan hydrateItems(MigrationPlan plan) {
        List<MigrationPlanItem> items = migrationPlanRepositoryPort.findItemsByMigrationPlanId(plan.getId());
        return plan.toBuilder().items(items).build();
    }

    private void validateTargetPlanVersion(UUID targetPlanVersionId) {
        try {
            PpmVersionMetaResult meta = ppmVersionMetaClient.getVersionMeta(targetPlanVersionId);
            if (!meta.versionActive()) {
                throw new BusinessRuleViolationException("Target PPM plan version is inactive: " + targetPlanVersionId);
            }
            if (!meta.planActive()) {
                throw new BusinessRuleViolationException("Target PPM plan is inactive for version: " + targetPlanVersionId);
            }
        } catch (BusinessRuleViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new PlanNotFoundException("Plan version not found in PPM: " + targetPlanVersionId);
        }
    }
}
