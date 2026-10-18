package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.model.SubscriptionScheduleFilter;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemorySubscriptionScheduleRepository implements SubscriptionScheduleRepositoryPort {

    private final ConcurrentHashMap<UUID, SubscriptionSchedule> store = new ConcurrentHashMap<>();

    @Override
    public SubscriptionSchedule save(SubscriptionSchedule schedule) {
        if (schedule.getId() == null) {
            schedule = schedule.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(schedule.getId(), schedule);
        return schedule;
    }

    @Override
    public Optional<SubscriptionSchedule> findPendingBySubscriptionIdAndActionType(UUID subscriptionId, SubscriptionScheduleActionType actionType) {
        return store.values().stream()
            .filter(s -> subscriptionId.equals(s.getSubscriptionId()))
            .filter(s -> actionType.equals(s.getActionType()))
            .filter(s -> s.getStatus() == SubscriptionScheduleStatus.PENDING)
            .findFirst();
    }

    @Override
    public List<SubscriptionSchedule> findDueSchedules(Instant asOf) {
        return store.values().stream()
            .filter(s -> s.getStatus() == SubscriptionScheduleStatus.PENDING)
            .filter(s -> s.getEffectiveAt() != null && !s.getEffectiveAt().isAfter(asOf))
            .toList();
    }

    @Override
    public PageResult<SubscriptionSchedule> findSchedules(SubscriptionScheduleFilter filter, int page, int size, String sortBy, String sortDirection) {
        List<SubscriptionSchedule> matched = store.values().stream()
            .filter(s -> filter == null || filter.tenantId() == null || filter.tenantId().equals(s.getTenantId()))
            .filter(s -> filter == null || filter.subscriptionId() == null || filter.subscriptionId().equals(s.getSubscriptionId()))
            .filter(s -> filter == null || filter.status() == null || filter.status().equals(s.getStatus()))
            .filter(s -> filter == null || filter.actionType() == null || filter.actionType().equals(s.getActionType()))
            .toList();

        int fromIndex = Math.min(page * size, matched.size());
        int toIndex = Math.min(fromIndex + size, matched.size());
        List<SubscriptionSchedule> content = matched.subList(fromIndex, toIndex);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) matched.size() / size);
        return new PageResult<>(content, page, size, matched.size(), totalPages, toIndex < matched.size());
    }
}
