package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionEventFilter;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemorySubscriptionEventRepository implements SubscriptionEventRepositoryPort {

    private final ConcurrentHashMap<UUID, SubscriptionEvent> store = new ConcurrentHashMap<>();

    @Override
    public SubscriptionEvent save(SubscriptionEvent event) {
        if (event.getId() == null) {
            event = event.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(event.getId(), event);
        return event;
    }

    @Override
    public PageResult<SubscriptionEvent> findEvents(SubscriptionEventFilter filter, int page, int size, String sortBy, String sortDirection) {
        List<SubscriptionEvent> matched = store.values().stream()
            .filter(e -> filter == null || filter.tenantId() == null || filter.tenantId().equals(e.getTenantId()))
            .filter(e -> filter == null || filter.subscriptionId() == null || filter.subscriptionId().equals(e.getSubscriptionId()))
            .filter(e -> filter == null || filter.eventType() == null || filter.eventType().equals(e.getEventType()))
            .filter(e -> filter == null || filter.dateFrom() == null || (e.getOccurredAt() != null && !e.getOccurredAt().isBefore(filter.dateFrom())))
            .filter(e -> filter == null || filter.dateTo() == null || (e.getOccurredAt() != null && !e.getOccurredAt().isAfter(filter.dateTo())))
            .toList();

        int fromIndex = Math.min(page * size, matched.size());
        int toIndex = Math.min(fromIndex + size, matched.size());
        List<SubscriptionEvent> content = matched.subList(fromIndex, toIndex);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) matched.size() / size);
        return new PageResult<>(content, page, size, matched.size(), totalPages, toIndex < matched.size());
    }
}
