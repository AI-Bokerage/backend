package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.Subscription;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for subscriptions — the anchor repository port for the subscription
 * aggregate, with finders supporting renewal, trial-expiry, and provider-sync scheduling.
 * Implementations must be thread-safe/stateless; concurrent writes to the same subscription
 * (e.g. scheduler vs. request thread) must surface as
 * {@link com.company.bsmsvc.domain.exception.ConcurrentUpdateException}.
 */
public interface SubscriptionRepositoryPort {

    Subscription save(Subscription subscription);

    Optional<Subscription> findCurrentByTenantId(UUID tenantId);

    Optional<Subscription> findById(UUID id);

    Optional<Subscription> findCurrentBySubscriptionId(UUID subscriptionId, Collection<com.company.bsmsvc.domain.enums.SubscriptionStatus> statuses);

    /** Returns ACTIVE subscriptions whose currentPeriodEnd is on or before the given instant. */
    List<Subscription> findDueForRenewal(Instant asOf);

    /**
     * Looks up a subscription by the provider-assigned subscription ID stored at sync time.
     * Used to correlate Stripe/Razorpay webhook events back to BSM subscriptions.
     */
    Optional<Subscription> findByExternalSubscriptionId(String externalSubscriptionId);

    /** Returns TRIALING subscriptions whose trialEndsAt is on or before the given instant. */
    List<Subscription> findExpiredTrials(Instant asOf);

    /**
     * Returns subscriptions that have no provider subscription ID yet (syncCreate failed
     * or was never attempted).  Only live statuses are returned — CANCELLED subscriptions
     * that were never synced do not need recovery.
     */
    List<Subscription> findPendingProviderSync();
}
