package io.genfin.dunning.port.retry;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import io.genfin.dunning.port.calendar.BusinessCalendar;
import java.time.ZoneId;

/**
 * The full, explicit policy governing how the Retry Engine schedules an obligation's retries: the
 * {@link BackoffStrategy} to space attempts, the {@link BusinessCalendar} attempts are rolled onto,
 * the time zone candidate instants are anchored in, and the maximum number of attempts before the
 * obligation is exhausted. An application resolves one of these from its own {@code DunningPolicy}
 * - fin-dunning never hardcodes any of these values.
 */
public interface RetryPolicy extends Extension {

  BackoffStrategy backoffStrategy();

  BusinessCalendar businessCalendar();

  ZoneId zone();

  /** Maximum number of retry attempts; attempt numbers beyond this are exhausted. */
  int maxRetries();
}
