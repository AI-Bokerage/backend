package io.genfin.dunning.internal.schedule;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.calendar.TimeWindow;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.port.calendar.BusinessCalendar;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.port.schedule.SchedulePolicy;
import io.genfin.dunning.port.schedule.ScheduleStrategy;
import io.genfin.dunning.retry.RetryCalculator;
import io.genfin.dunning.retry.RetryDecision;
import io.genfin.dunning.retry.RetryPlan;
import io.genfin.dunning.retry.RetryStrategies;
import io.genfin.dunning.schedule.DunningSchedule;
import io.genfin.dunning.schedule.ScheduledEvent;
import io.genfin.dunning.schedule.ScheduledEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Composes a {@link SchedulePolicy}'s grace period, reminder offsets, and retry policy into one
 * coherent {@link DunningSchedule}: the grace-period-end instant and every reminder offset are
 * rolled onto the retry policy's {@code BusinessCalendar} the same way retry attempts are, and
 * retries themselves are planned (via {@link RetryCalculator}) from the grace period's expiry - so
 * retries never start before grace has elapsed.
 */
public final class DefaultScheduleStrategy implements ScheduleStrategy {

  @Override
  public DunningSchedule schedule(FinancialObligation obligation, SchedulePolicy policy) {
    Validate.notNull(obligation, "obligation must not be null.");
    Validate.notNull(policy, "policy must not be null.");

    RetryPolicy retryPolicy = policy.retryPolicy();
    BusinessCalendar calendar = retryPolicy.businessCalendar();
    ZoneId zone = retryPolicy.zone();

    Instant graceExpiry = policy.gracePeriod().expiresAt(obligation.dueDate());

    List<ScheduledEvent> events = new ArrayList<>();
    events.add(
        new ScheduledEvent(
            ScheduledEventType.GRACE_PERIOD_END, 1, rollToWindow(graceExpiry, zone, calendar)));

    List<BackoffInterval> reminderOffsets = policy.reminderOffsets();
    for (int i = 0; i < reminderOffsets.size(); i++) {
      Instant candidate = obligation.dueDate().plus(reminderOffsets.get(i).toDuration());
      events.add(
          new ScheduledEvent(
              ScheduledEventType.REMINDER, i + 1, rollToWindow(candidate, zone, calendar)));
    }

    RetryPlan retryPlan =
        new RetryCalculator(RetryStrategies.standard()).plan(retryPolicy, graceExpiry);
    for (RetryDecision decision : retryPlan.decisions()) {
      if (!decision.exhausted()) {
        events.add(
            new ScheduledEvent(
                ScheduledEventType.RETRY, decision.attemptNumber(), decision.window().window()));
      }
    }

    return DunningSchedule.of(events);
  }

  private static TimeWindow rollToWindow(
      Instant candidate, ZoneId zone, BusinessCalendar calendar) {
    LocalDate day = candidate.atZone(zone).toLocalDate();
    LocalDate rolledDay = calendar.roll(day);
    Instant start = rolledDay.atStartOfDay(zone).toInstant();
    Instant end = rolledDay.plusDays(1).atStartOfDay(zone).toInstant();
    return TimeWindow.of(start, end);
  }
}
