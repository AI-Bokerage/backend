package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.LimitSnapshotFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionLimitSnapshot;

/**
 * Persistence/query port for point-in-time snapshots of a subscription's usage limits (captured
 * around plan changes for auditing/diagnostics). Implementations must be thread-safe/stateless.
 */
public interface SubscriptionLimitSnapshotRepositoryPort {

    SubscriptionLimitSnapshot save(SubscriptionLimitSnapshot snapshot);

    PageResult<SubscriptionLimitSnapshot> findSnapshots(LimitSnapshotFilter filter, int page, int size, String sortBy, String sortDirection);
}
