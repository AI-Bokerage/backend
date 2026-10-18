package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.application.service.CommercialEngineService;
import com.company.bsmsvc.application.service.SubscriptionSynchronizationService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.ProrationMode;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.event.DowngradePreflightExecutedEvent;
import com.company.bsmsvc.domain.event.SubscriptionUpgradedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.model.DowngradeImpact;
import com.company.bsmsvc.domain.model.DowngradeImpactDetails;
import com.company.bsmsvc.domain.model.DowngradeWarning;
import com.company.bsmsvc.domain.model.LimitSnapshotFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.ProrationPreview;
import com.company.bsmsvc.domain.model.ProrationPreviewFilter;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionLimitSnapshot;
import com.company.bsmsvc.domain.port.FeatureEntitlementPort;
import com.company.bsmsvc.domain.port.ProjectUsagePort;
import com.company.bsmsvc.domain.port.ProrationPreviewRepositoryPort;
import com.company.bsmsvc.domain.port.StorageUsagePort;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionLimitSnapshotRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.UserUsagePort;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import com.company.bsmsvc.domain.port.PlanLimitsPort;
import com.company.bsmsvc.domain.model.PpmPlanLimitsResult;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import com.company.bsmsvc.domain.port.TenantScopePort;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import io.cpms.common.plan.PpmTierRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialEngineServiceImpl implements CommercialEngineService {

    private static final String SYSTEM_ACTOR = "commercial-engine";

    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    private final SubscriptionEventRepositoryPort subscriptionEventRepositoryPort;
    private final ProrationPreviewRepositoryPort prorationPreviewRepositoryPort;
    private final SubscriptionLimitSnapshotRepositoryPort subscriptionLimitSnapshotRepositoryPort;
    private final UserUsagePort userUsagePort;
    private final ProjectUsagePort projectUsagePort;
    private final StorageUsagePort storageUsagePort;
    private final FeatureEntitlementPort featureEntitlementPort;
    private final SubscriptionLifecycleMapper subscriptionLifecycleMapper;
    private final TenantOwnershipValidator tenantOwnershipValidator;
    private final SubscriptionSynchronizationService subscriptionSynchronizationService;
    private final TenantBillingProfileService tenantBillingProfileService;
    private final TenantScopePort tenantScopeEnforcer;
    private final SubscriptionEventPublisherPort subscriptionEventPublisher;
    private final PlanVersionMetaPort ppmVersionMetaClient;
    private final PlanLimitsPort  ppmPlanLimitsClient;

    @Override
    @Transactional
    public Subscription upgradeSubscription(
        UUID subscriptionId,
        UUID tenantId,
        UUID targetPlanVersionId,
        String reason,
        String performedBy
    ) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        Subscription subscription = getActiveSubscription(subscriptionId, tenantId);
        Instant now = Instant.now();

        if (subscription.getPpmPlanVersionId() != null) {
            return upgradeSubscriptionPpm(subscription, targetPlanVersionId, reason, performedBy, now);
        }

        throw new BusinessRuleViolationException("Upgrade is only supported for PPM-backed subscriptions");
    }

    @Override
    @Transactional
    public Subscription applyScheduledPlanChange(
        UUID subscriptionId,
        UUID tenantId,
        UUID targetPlanVersionId,
        String reason
    ) {
        // Called by the schedule executor — no JWT in context so assertTenantAccess is bypassed
        Subscription subscription = getActiveSubscription(subscriptionId, tenantId);
        Instant now = Instant.now();

        if (subscription.getPpmPlanVersionId() != null) {
            return applyScheduledPlanChangePpm(subscription, targetPlanVersionId, reason, now);
        }

        throw new BusinessRuleViolationException("applyScheduledPlanChange is only supported for PPM-backed subscriptions");
    }

    @Override
    @Transactional
    public ProrationPreview generateProrationPreview(
        UUID subscriptionId,
        UUID tenantId,
        UUID targetPlanVersionId,
        ProrationMode mode
    ) {
        throw new BusinessRuleViolationException("Proration preview is not supported for PPM-backed subscriptions");
    }

    @Override
    @Transactional
    public DowngradeImpact executeDowngradePreflight(UUID subscriptionId, UUID tenantId, UUID targetPlanVersionId) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        Subscription subscription = getActiveSubscription(subscriptionId, tenantId);
        Instant now = Instant.now();

        // PPM-backed subscriptions: limits come from PPM /limits; tier check is deferred (open question).
        // BSM-native subscriptions: limits come from the BSM local plan_versions catalog.
        // planVersionId is legacy (dropped NOT NULL in V35, superseded by ppmPlanVersionId) and is
        // null for every PPM-backed subscription — check ppmPlanVersionId first, not planVersionId,
        // or this NPEs for all current traffic before ever reaching the branch that handles it.
        if (subscription.getPpmPlanVersionId() != null) {
            if (subscription.getPpmPlanVersionId().equals(targetPlanVersionId)) {
                throw new BusinessRuleViolationException("Target plan version must be different from the current subscription plan version");
            }
            return executeDowngradePreflightPpm(subscription, targetPlanVersionId, now);
        }
        throw new BusinessRuleViolationException("Downgrade preflight is only supported for PPM-backed subscriptions");
    }

    private Subscription upgradeSubscriptionPpm(
            Subscription subscription, UUID targetPlanVersionId,
            String reason, String performedBy, Instant now) {

        if (subscription.getPpmPlanVersionId().equals(targetPlanVersionId)) {
            throw new BusinessRuleViolationException("Target plan version must be different from the current subscription plan version");
        }

        PpmVersionMetaResult currentMeta = ppmVersionMetaClient.getVersionMeta(subscription.getPpmPlanVersionId());
        PpmVersionMetaResult targetMeta  = ppmVersionMetaClient.getVersionMeta(targetPlanVersionId);

        if (!targetMeta.versionActive() || !targetMeta.planActive()) {
            throw new BusinessRuleViolationException("Target plan version is not active: " + targetPlanVersionId);
        }

        if (!PpmTierRegistry.isHigherTier(currentMeta.tier(), targetMeta.tier())) {
            throw new BusinessRuleViolationException(
                "Target plan tier must be higher than current tier. current=" + currentMeta.tier() + ", target=" + targetMeta.tier());
        }

        UUID prevVersionId = subscription.getPpmPlanVersionId();
        Subscription upgraded = subscription.toBuilder()
            .ppmPlanVersionId(targetPlanVersionId)
            .updatedAt(now)
            .build();
        Subscription saved = subscriptionRepositoryPort.save(upgraded);

        UUID actorId = toActorUuid(performedBy);
        createHistory(saved, SubscriptionHistoryAction.SUBSCRIPTION_UPGRADED, prevVersionId, targetPlanVersionId, reason, performedBy, actorId, ActorType.USER, now);
        createEvent(saved, SubscriptionEventType.SUBSCRIPTION_UPGRADED, Map.of(
            "reason", safe(reason),
            "performedBy", safe(performedBy),
            "fromPlanVersionId", prevVersionId.toString(),
            "toPlanVersionId", targetPlanVersionId.toString()
        ), actorId, ActorType.USER, now);

        saved.registerEvent(new SubscriptionUpgradedEvent(saved.getId(), saved.getTenantId(), prevVersionId, targetPlanVersionId, now));
        log.info("[upgradeSubscription] PPM SUCCESS subscriptionId={} tenantId={} fromPpmVersionId={} toPpmVersionId={}",
            saved.getId(), saved.getTenantId(), prevVersionId, targetPlanVersionId);

        subscriptionEventPublisher.publishUpgraded(saved, prevVersionId);

        final Subscription toSync = saved;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { subscriptionSynchronizationService.syncUpdate(toSync); }
                    catch (Exception e) { log.error("[upgradeSubscription] PPM syncUpdate failed for subscriptionId={}", toSync.getId(), e); }
                }
            });
        } else {
            subscriptionSynchronizationService.syncUpdate(toSync);
        }
        return saved;
    }

    private Subscription applyScheduledPlanChangePpm(
            Subscription subscription, UUID targetPlanVersionId,
            String reason, Instant now) {

        if (subscription.getPpmPlanVersionId().equals(targetPlanVersionId)) {
            throw new BusinessRuleViolationException("Target plan version is the same as current: " + targetPlanVersionId);
        }

        PpmVersionMetaResult currentMeta = ppmVersionMetaClient.getVersionMeta(subscription.getPpmPlanVersionId());
        PpmVersionMetaResult targetMeta  = ppmVersionMetaClient.getVersionMeta(targetPlanVersionId);

        if (!targetMeta.versionActive() || !targetMeta.planActive()) {
            throw new BusinessRuleViolationException("Target plan version is not active: " + targetPlanVersionId);
        }

        if (!PpmTierRegistry.isLowerTier(currentMeta.tier(), targetMeta.tier())) {
            throw new BusinessRuleViolationException(
                "DOWNGRADE_SUBSCRIPTION schedule requires a lower-tier target. " +
                "current=" + currentMeta.tier() + ", target=" + targetMeta.tier() +
                ". Use upgradeSubscription() for upgrades.");
        }

        UUID prevVersionId = subscription.getPpmPlanVersionId();
        Subscription changed = subscription.toBuilder()
            .ppmPlanVersionId(targetPlanVersionId)
            .updatedAt(now)
            .build();
        Subscription saved = subscriptionRepositoryPort.save(changed);

        UUID actorId = systemActorId();
        createHistory(saved, SubscriptionHistoryAction.SUBSCRIPTION_DOWNGRADED, prevVersionId, targetPlanVersionId,
            reason, SYSTEM_ACTOR, actorId, ActorType.SYSTEM, now);
        createEvent(saved, SubscriptionEventType.SUBSCRIPTION_DOWNGRADED, Map.of(
            "reason", safe(reason),
            "performedBy", SYSTEM_ACTOR,
            "fromPlanVersionId", prevVersionId.toString(),
            "toPlanVersionId", targetPlanVersionId.toString(),
            "scheduledExecution", "true"
        ), actorId, ActorType.SYSTEM, now);

        subscriptionEventPublisher.publishUpgraded(saved, prevVersionId);

        final Subscription toSync = saved;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { subscriptionSynchronizationService.syncUpdate(toSync); }
                    catch (Exception e) { log.error("[applyScheduledPlanChange] PPM syncUpdate failed for subscriptionId={}", toSync.getId(), e); }
                }
            });
        } else {
            subscriptionSynchronizationService.syncUpdate(toSync);
        }
        log.info("[applyScheduledPlanChange] PPM SUCCESS subscriptionId={} tenantId={} {} → {}",
            saved.getId(), saved.getTenantId(), currentMeta.tier(), targetMeta.tier());
        return saved;
    }

    private DowngradeImpact executeDowngradePreflightPpm(
            Subscription subscription, UUID targetPlanVersionId, Instant now) {

        PpmVersionMetaResult targetMeta = ppmVersionMetaClient.getVersionMeta(targetPlanVersionId);
        if (!targetMeta.versionActive() || !targetMeta.planActive()) {
            throw new BusinessRuleViolationException(
                "Target plan version is not active: " + targetPlanVersionId);
        }
        // Tier validation is skipped for PPM-backed subscriptions — PPM does not yet expose
        // tier in the /meta response. Tracked as an open question for a future PPM endpoint.
        log.warn("[executeDowngradePreflight] Tier validation skipped for PPM-backed subscriptionId={}",
            subscription.getId());

        PpmPlanLimitsResult limits = ppmPlanLimitsClient.getLimits(targetPlanVersionId);

        com.company.bsmsvc.domain.model.UserUsageCounts usage =
            userUsagePort.getUserUsageCounts(subscription.getTenantId());
        int internalUsers     = usage.activeInternalUsers();
        int clientUsers       = usage.activeClientUsers();
        int activeProjects    = projectUsagePort.getActiveProjectCount(subscription.getTenantId());
        long usedStorageBytes = storageUsagePort.getUsedStorageBytes(subscription.getTenantId());
        List<String> activeFeatures = featureEntitlementPort.getActiveFeatureCodes(subscription.getTenantId());

        int maxInternalUsers   = limits.maxInternalUsers()   != null ? limits.maxInternalUsers()   : Integer.MAX_VALUE;
        int maxClientUsers     = limits.maxClientUsers()     != null ? limits.maxClientUsers()     : Integer.MAX_VALUE;
        int maxActiveProjects  = limits.maxActiveProjects()  != null ? limits.maxActiveProjects()  : Integer.MAX_VALUE;
        long storageQuotaBytes = limits.storageQuotaBytes()  != null ? limits.storageQuotaBytes()  : Long.MAX_VALUE;

        int usersOverLimit = Math.max(0, internalUsers - maxInternalUsers)
            + Math.max(0, clientUsers - maxClientUsers);
        int totalActiveUsers      = internalUsers + clientUsers;
        int totalTargetUserLimit  = (maxInternalUsers == Integer.MAX_VALUE ? 0 : maxInternalUsers)
            + (maxClientUsers == Integer.MAX_VALUE ? 0 : maxClientUsers);
        int projectsOverLimit     = maxActiveProjects == Integer.MAX_VALUE ? 0
            : Math.max(0, activeProjects - maxActiveProjects);
        long storageOverLimitBytes = storageQuotaBytes == Long.MAX_VALUE ? 0L
            : Math.max(0L, usedStorageBytes - storageQuotaBytes);

        List<String> lostFeatures = determineLostFeaturesPpm(activeFeatures, limits);
        List<DowngradeWarning> warnings = buildWarnings(
            usersOverLimit, totalActiveUsers, totalTargetUserLimit,
            projectsOverLimit, storageOverLimitBytes, lostFeatures);

        Map<String, Object> limitsSnapshot = buildLimitsSnapshotPpm(limits);
        Map<String, Object> usageSnapshot  = buildUsageSnapshot(
            internalUsers, clientUsers, activeProjects, usedStorageBytes, activeFeatures);
        boolean overLimit = usersOverLimit > 0 || projectsOverLimit > 0
            || storageOverLimitBytes > 0 || !lostFeatures.isEmpty();
        createLimitSnapshot(subscription.getId(), targetPlanVersionId, limitsSnapshot, usageSnapshot, overLimit);

        DowngradeImpact impact = new DowngradeImpact(
            new DowngradeImpactDetails(usersOverLimit, projectsOverLimit, storageOverLimitBytes, lostFeatures),
            warnings);

        UUID actorId = systemActorId();
        createHistory(subscription, SubscriptionHistoryAction.DOWNGRADE_PREFLIGHT_EXECUTED,
            subscription.getPlanVersionId(), targetPlanVersionId,
            "Executed downgrade preflight (PPM)", SYSTEM_ACTOR, actorId, ActorType.SYSTEM, now);
        createEvent(subscription, SubscriptionEventType.DOWNGRADE_PREFLIGHT_EXECUTED, Map.of(
            "targetPlanVersionId", targetPlanVersionId.toString(),
            "usersOverLimit", usersOverLimit,
            "projectsOverLimit", projectsOverLimit,
            "storageOverLimitBytes", storageOverLimitBytes,
            "featuresLost", lostFeatures,
            "source", "PPM"
        ), actorId, ActorType.SYSTEM, now);

        subscription.registerEvent(new DowngradePreflightExecutedEvent(
            subscription.getId(), subscription.getTenantId(), targetPlanVersionId, impact.isOverLimit(), now));
        log.info("[executeDowngradePreflight] PPM SUCCESS subscriptionId={} targetPlanVersionId={} overLimit={}",
            subscription.getId(), targetPlanVersionId, impact.isOverLimit());
        return impact;
    }

    @Override
    @Transactional
    public SubscriptionLimitSnapshot createLimitSnapshot(
        UUID subscriptionId,
        UUID planVersionId,
        Map<String, Object> limitsSnapshot,
        Map<String, Object> usageSnapshot,
        boolean overLimit
    ) {
        SubscriptionLimitSnapshot snapshot = SubscriptionLimitSnapshot.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscriptionId)
            .planVersionId(planVersionId)
            .limitsSnapshot(limitsSnapshot)
            .usageSnapshot(usageSnapshot)
            .overLimit(overLimit)
            .createdAt(Instant.now())
            .build();
        return subscriptionLimitSnapshotRepositoryPort.save(snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProrationPreview> getProrationPreviews(
        ProrationPreviewFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDir
    ) {
        return prorationPreviewRepositoryPort.findPreviews(filter, page, size, sortBy, sortDir);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SubscriptionLimitSnapshot> getLimitSnapshots(
        LimitSnapshotFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDir
    ) {
        return subscriptionLimitSnapshotRepositoryPort.findSnapshots(filter, page, size, sortBy, sortDir);
    }

    private Subscription getActiveSubscription(UUID subscriptionId, UUID tenantId) {
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found: " + subscriptionId));
        tenantOwnershipValidator.validate(subscription, tenantId);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Subscription must be ACTIVE to perform commercial engine operations");
        }
        return subscription;
    }

    private List<DowngradeWarning> buildWarnings(
        int usersOverLimit,
        int totalActiveUsers,
        int totalTargetUserLimit,
        int projectsOverLimit,
        long storageOverLimitBytes,
        List<String> lostFeatures
    ) {
        List<DowngradeWarning> warnings = new ArrayList<>();
        if (usersOverLimit > 0) {
            warnings.add(new DowngradeWarning(
                "USER_LIMIT_EXCEEDED",
                "You currently have " + totalActiveUsers + " active employees. "
                + "The selected plan allows only " + totalTargetUserLimit + " active employees. "
                + "Please deactivate at least " + usersOverLimit + " employees before downgrading."
            ));
        }
        if (projectsOverLimit > 0) {
            warnings.add(new DowngradeWarning("PROJECT_LIMIT_EXCEEDED", projectsOverLimit + " active projects exceed the target plan limit"));
        }
        if (storageOverLimitBytes > 0) {
            warnings.add(new DowngradeWarning("STORAGE_LIMIT_EXCEEDED", storageOverLimitBytes + " bytes exceed the target plan quota"));
        }
        if (!lostFeatures.isEmpty()) {
            warnings.add(new DowngradeWarning("FEATURES_WILL_BE_LOST", "The following features will be lost: " + String.join(", ", lostFeatures)));
        }
        return warnings;
    }

    private List<String> determineLostFeaturesPpm(List<String> activeFeatures, PpmPlanLimitsResult limits) {
        Set<String> activeFeatureSet = Set.copyOf(activeFeatures);
        List<String> lostFeatures = new ArrayList<>();
        if (activeFeatureSet.contains("CUSTOM_DOMAIN") && !Boolean.TRUE.equals(limits.customDomainEnabled())) {
            lostFeatures.add("CUSTOM_DOMAIN");
        }
        if (activeFeatureSet.contains("SSO") && !Boolean.TRUE.equals(limits.ssoEnabled())) {
            lostFeatures.add("SSO");
        }
        if (activeFeatureSet.contains("PRIORITY_SUPPORT") && !Boolean.TRUE.equals(limits.prioritySupport())) {
            lostFeatures.add("PRIORITY_SUPPORT");
        }
        return lostFeatures;
    }

    private Map<String, Object> buildLimitsSnapshotPpm(PpmPlanLimitsResult limits) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("maxInternalUsers", limits.maxInternalUsers());
        snapshot.put("maxClientUsers", limits.maxClientUsers());
        snapshot.put("maxActiveProjects", limits.maxActiveProjects());
        snapshot.put("storageQuotaBytes", limits.storageQuotaBytes());
        snapshot.put("customDomainEnabled", limits.customDomainEnabled());
        snapshot.put("ssoEnabled", limits.ssoEnabled());
        snapshot.put("prioritySupport", limits.prioritySupport());
        return snapshot;
    }

    private Map<String, Object> buildUsageSnapshot(
        int internalUsers,
        int clientUsers,
        int activeProjects,
        long usedStorageBytes,
        List<String> activeFeatures
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("activeInternalUsers", internalUsers);
        snapshot.put("activeClientUsers", clientUsers);
        snapshot.put("activeProjects", activeProjects);
        snapshot.put("usedStorageBytes", usedStorageBytes);
        snapshot.put("activeFeatures", activeFeatures);
        return snapshot;
    }

    private void createHistory(
        Subscription subscription,
        SubscriptionHistoryAction action,
        UUID fromPlanVersionId,
        UUID toPlanVersionId,
        String reason,
        String performedBy,
        UUID actorId,
        ActorType actorType,
        Instant occurredAt
    ) {
        subscriptionHistoryRepositoryPort.save(
            subscriptionLifecycleMapper.toHistory(subscription, action, fromPlanVersionId, toPlanVersionId, reason, performedBy, actorId, actorType, occurredAt)
        );
    }

    private void createEvent(
        Subscription subscription,
        SubscriptionEventType eventType,
        Map<String, Object> payload,
        UUID actorId,
        ActorType actorType,
        Instant occurredAt
    ) {
        subscriptionEventRepositoryPort.save(
            subscriptionLifecycleMapper.toEvent(subscription, eventType, payload, actorId, actorType, occurredAt)
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private UUID systemActorId() {
        return toActorUuid(SYSTEM_ACTOR);
    }

    private UUID toActorUuid(String actor) {
        try {
            return UUID.fromString(actor);
        } catch (Exception ex) {
            return UUID.nameUUIDFromBytes(safe(actor).getBytes(StandardCharsets.UTF_8));
        }
    }
}
