package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.enums.ProrationMode;
import com.company.bsmsvc.domain.model.DowngradeImpact;
import com.company.bsmsvc.domain.model.LimitSnapshotFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.ProrationPreview;
import com.company.bsmsvc.domain.model.ProrationPreviewFilter;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionLimitSnapshot;
import java.util.UUID;

public interface CommercialEngineService {

    /**
     * Upgrades a subscription to a higher-tier plan version.
     * Validates ownership, active status, tier ordering, and logs an audit trail.
     */
    Subscription upgradeSubscription(
        UUID subscriptionId,
        UUID tenantId,
        UUID targetPlanVersionId,
        String reason,
        String performedBy
    );

    /**
     * Applies a scheduled plan change regardless of tier direction (up or down).
     * Used exclusively by the {@code SubscriptionScheduleExecutorScheduler} to
     * execute a {@code DOWNGRADE_SUBSCRIPTION} schedule at its effective date.
     * Does NOT perform a preflight check — that must have been done when the schedule was created.
     */
    Subscription applyScheduledPlanChange(
        UUID subscriptionId,
        UUID tenantId,
        UUID targetPlanVersionId,
        String reason
    );

    /**
     * Calculates and persists a proration preview for a prospective plan change.
     */
    ProrationPreview generateProrationPreview(
        UUID subscriptionId,
        UUID tenantId,
        UUID targetPlanVersionId,
        ProrationMode mode
    );

    default ProrationPreview previewProration(
        UUID subscriptionId,
        UUID tenantId,
        UUID targetPlanVersionId,
        ProrationMode mode
    ) {
        return generateProrationPreview(subscriptionId, tenantId, targetPlanVersionId, mode);
    }

    /**
     * Runs preflight analysis for a downgrade: computes resource over-limits and feature losses.
     * Saves a limit snapshot and returns the impact details.
     */
    DowngradeImpact executeDowngradePreflight(
        UUID subscriptionId,
        UUID tenantId,
        UUID targetPlanVersionId
    );

    SubscriptionLimitSnapshot createLimitSnapshot(
        UUID subscriptionId,
        UUID planVersionId,
        java.util.Map<String, Object> limitsSnapshot,
        java.util.Map<String, Object> usageSnapshot,
        boolean overLimit
    );

    PageResult<ProrationPreview> getProrationPreviews(
        ProrationPreviewFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDir
    );

    PageResult<SubscriptionLimitSnapshot> getLimitSnapshots(
        LimitSnapshotFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDir
    );
}
