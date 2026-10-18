package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.ApplyPlanChangeCommand;
import com.company.bsmsvc.domain.model.PlanChangeApplyResult;
import com.company.bsmsvc.domain.model.PlanChangePreviewResult;
import com.company.bsmsvc.domain.model.PreviewPlanChangeCommand;
import java.util.UUID;

public interface SubscriptionChangeService {

    /**
     * Pure read-only preview — no writes, no side effects.
     * Returns proration calculation and change type for display to the user.
     */
    PlanChangePreviewResult previewChange(UUID subscriptionId, PreviewPlanChangeCommand command);

    /**
     * Applies a PPM plan change atomically.
     * For upgrades (net > 0): creates an adjustment invoice and returns a checkout URL.
     * For downgrades (net <= 0): updates PPM fields immediately; new price takes effect at next renewal.
     */
    PlanChangeApplyResult applyChange(UUID subscriptionId, ApplyPlanChangeCommand command);
}
