package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.SubscriptionHistoryFilter;
import com.company.bsmsvc.domain.model.PageResult;

/**
 * Persistence/query port for a subscription's state-transition history (status changes,
 * plan changes, dunning progression over time). Implementations must be thread-safe/stateless.
 */
public interface SubscriptionHistoryRepositoryPort {

    SubscriptionHistory save(SubscriptionHistory history);

    PageResult<SubscriptionHistory> findHistory(SubscriptionHistoryFilter filter, int page, int size, String sortBy, String sortDirection);
}
