package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.Subscription;

public interface SubscriptionSynchronizationService {

    /**
     * Creates a provider subscription using the PlanVersion's provider price/plan ID.
     * Throws BusinessRuleViolationException if provider mapping is not configured.
     */
    Subscription syncCreate(Subscription subscription);

    /**
     * Updates the provider subscription to the plan version currently on the subscription.
     * Throws BusinessRuleViolationException if provider mapping is not configured.
     * No-op if subscription has no externalSubscriptionId.
     */
    Subscription syncUpdate(Subscription subscription);

    /**
     * Cancels the provider subscription.
     * No-op if subscription has no externalSubscriptionId.
     */
    void syncCancel(Subscription subscription, boolean immediately);
}
