package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionEventFilter;

/**
 * Persistence/query port for subscription audit events (a searchable event log distinct from
 * {@link SubscriptionHistoryRepositoryPort}'s state-transition history). Implementations must
 * be thread-safe/stateless.
 */
public interface SubscriptionEventRepositoryPort {

    SubscriptionEvent save(SubscriptionEvent event);

    PageResult<SubscriptionEvent> findEvents(SubscriptionEventFilter filter, int page, int size, String sortBy, String sortDirection);
}
