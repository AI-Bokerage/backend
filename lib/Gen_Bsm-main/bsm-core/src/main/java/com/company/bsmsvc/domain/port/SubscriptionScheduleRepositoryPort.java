package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.model.SubscriptionScheduleFilter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for scheduled subscription actions (e.g. a scheduled downgrade taking
 * effect at the next billing period), including due-schedule lookups consumed by the scheduler
 * executor. Implementations must be thread-safe/stateless.
 */
public interface SubscriptionScheduleRepositoryPort {

    SubscriptionSchedule save(SubscriptionSchedule schedule);

    Optional<SubscriptionSchedule> findPendingBySubscriptionIdAndActionType(UUID subscriptionId, SubscriptionScheduleActionType actionType);

    /** Returns PENDING schedules whose effectiveAt is on or before the given instant. */
    List<SubscriptionSchedule> findDueSchedules(Instant asOf);

    PageResult<SubscriptionSchedule> findSchedules(SubscriptionScheduleFilter filter, int page, int size, String sortBy, String sortDirection);
}
