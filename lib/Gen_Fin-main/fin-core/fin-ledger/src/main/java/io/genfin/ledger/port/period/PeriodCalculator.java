package io.genfin.ledger.port.period;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.period.AccountingPeriod;
import io.genfin.ledger.period.PeriodGranularity;
import java.util.List;

/**
 * The Accounting Period Engine: builds {@link AccountingPeriod}s from a {@link PeriodPolicy}'s
 * fiscal calendar. Never decides the calendar itself - that is entirely the injected {@link
 * PeriodPolicy}'s responsibility, mirroring how {@code
 * io.genfin.ledger.port.balance.BalanceCalculator} never re-derives which entries to include, only
 * rolls up what the injected {@code BalancePolicy} accepts.
 */
public interface PeriodCalculator extends Extension {

  /** The {@code granularity} period containing {@code instant}. */
  AccountingPeriod periodFor(OccurredAt instant, PeriodGranularity granularity);

  /** The period immediately following {@code period}, at the same granularity. */
  AccountingPeriod next(AccountingPeriod period);

  /** The period immediately preceding {@code period}, at the same granularity. */
  AccountingPeriod previous(AccountingPeriod period);

  /**
   * Every consecutive {@code granularity} period needed to cover {@code [start, end)}, oldest
   * first.
   */
  List<AccountingPeriod> periodsBetween(
      OccurredAt start, OccurredAt end, PeriodGranularity granularity);
}
