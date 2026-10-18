package io.genfin.dunning.port.schedule;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.calendar.GracePeriod;
import io.genfin.dunning.port.retry.RetryPolicy;
import java.util.List;

/**
 * The full, explicit policy governing how the Schedule Engine composes an obligation's overall
 * collection schedule: the {@link GracePeriod} before dunning begins, the resolved {@link
 * RetryPolicy} (itself carrying the {@code BackoffStrategy}, {@code BusinessCalendar}, zone, and
 * max retries retries are planned against), and the reminder offsets to schedule during/after that
 * grace period. An application resolves one of these from its own {@code DunningPolicy} - fin-
 * dunning never hardcodes any of these values.
 */
public interface SchedulePolicy extends Extension {

  GracePeriod gracePeriod();

  RetryPolicy retryPolicy();

  /**
   * Offsets (from the obligation's due date) at which a reminder is scheduled, in an arbitrary time
   * unit each - empty when this obligation's resolved policy schedules no reminders at all.
   */
  List<BackoffInterval> reminderOffsets();
}
