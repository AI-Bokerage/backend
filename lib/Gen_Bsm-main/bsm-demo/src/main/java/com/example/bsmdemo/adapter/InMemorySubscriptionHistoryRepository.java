package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.SubscriptionHistoryFilter;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemorySubscriptionHistoryRepository implements SubscriptionHistoryRepositoryPort {

    private final ConcurrentHashMap<UUID, SubscriptionHistory> store = new ConcurrentHashMap<>();

    @Override
    public SubscriptionHistory save(SubscriptionHistory history) {
        if (history.getId() == null) {
            history = history.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(history.getId(), history);
        return history;
    }

    @Override
    public PageResult<SubscriptionHistory> findHistory(SubscriptionHistoryFilter filter, int page, int size, String sortBy, String sortDirection) {
        List<SubscriptionHistory> matched = store.values().stream()
            .filter(h -> filter == null || filter.tenantId() == null || filter.tenantId().equals(h.getTenantId()))
            .filter(h -> filter == null || filter.subscriptionId() == null || filter.subscriptionId().equals(h.getSubscriptionId()))
            .filter(h -> filter == null || filter.action() == null || filter.action().equals(h.getAction()))
            .filter(h -> filter == null || filter.dateFrom() == null || (h.getOccurredAt() != null && !h.getOccurredAt().isBefore(filter.dateFrom())))
            .filter(h -> filter == null || filter.dateTo() == null || (h.getOccurredAt() != null && !h.getOccurredAt().isAfter(filter.dateTo())))
            .toList();

        int fromIndex = Math.min(page * size, matched.size());
        int toIndex = Math.min(fromIndex + size, matched.size());
        List<SubscriptionHistory> content = matched.subList(fromIndex, toIndex);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) matched.size() / size);
        return new PageResult<>(content, page, size, matched.size(), totalPages, toIndex < matched.size());
    }
}
