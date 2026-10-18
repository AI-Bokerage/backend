package io.genfin.dunning.schedule;

/**
 * The kind of point-in-time entry a {@link DunningSchedule} carries. Structural to the schedule
 * shape only - which offsets/backoff/calendar rules actually produce these entries is entirely
 * driven by the resolved {@code SchedulePolicy}, never hardcoded here.
 */
public enum ScheduledEventType {

  /** The single instant the obligation's {@code GracePeriod} elapses. */
  GRACE_PERIOD_END,

  /** One of the resolved reminder offsets, calendar-adjusted. */
  REMINDER,

  /** One retry attempt from the resolved {@code RetryPlan}. */
  RETRY
}
