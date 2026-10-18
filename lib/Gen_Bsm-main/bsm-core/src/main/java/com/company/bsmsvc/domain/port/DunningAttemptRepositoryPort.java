package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.DunningAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for dunning attempts (scheduled payment-retry records for
 * subscriptions in the failed-payment recovery flow). Implementations must be
 * thread-safe/stateless — {@code save} may race with concurrent scheduler executions on the
 * same attempt; callers translate optimistic-lock conflicts into
 * {@link com.company.bsmsvc.domain.exception.ConcurrentUpdateException}.
 */
public interface DunningAttemptRepositoryPort {
    DunningAttempt save(DunningAttempt attempt);
    Optional<DunningAttempt> findById(UUID id);
    List<DunningAttempt> findBySubscriptionId(UUID subscriptionId);
    Optional<DunningAttempt> findLatestBySubscriptionId(UUID subscriptionId);
    List<DunningAttempt> findDuePending(Instant now);
}
