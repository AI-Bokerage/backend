package io.genfin.ledger.internal.period;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.period.AccountingPeriod;
import io.genfin.ledger.period.PeriodGranularity;
import io.genfin.ledger.period.StandardPeriodStatus;
import io.genfin.ledger.port.period.PeriodCalculator;
import io.genfin.ledger.port.period.PeriodPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link PeriodCalculator}: builds every {@link AccountingPeriod} from the boundaries and
 * fiscal year the injected {@link PeriodPolicy} computes, newly-generated {@link
 * StandardPeriodStatus#OPEN}. Carries no fiscal-calendar knowledge of its own.
 */
public final class DefaultPeriodCalculator implements PeriodCalculator {

  private final PeriodPolicy policy;

  public DefaultPeriodCalculator(PeriodPolicy policy) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
  }

  @Override
  public AccountingPeriod periodFor(OccurredAt instant, PeriodGranularity granularity) {
    Validate.notNull(instant, "instant must not be null.");
    Validate.notNull(granularity, "granularity must not be null.");
    OccurredAt start = policy.periodStart(instant, granularity);
    OccurredAt end = policy.periodEnd(instant, granularity);
    return new AccountingPeriod(
        AccountingPeriodId.generate(),
        granularity,
        policy.fiscalYearOf(instant),
        start,
        end,
        StandardPeriodStatus.OPEN);
  }

  @Override
  public AccountingPeriod next(AccountingPeriod period) {
    Validate.notNull(period, "period must not be null.");
    return periodFor(period.end(), period.granularity());
  }

  @Override
  public AccountingPeriod previous(AccountingPeriod period) {
    Validate.notNull(period, "period must not be null.");
    Instant justBeforeStart = period.start().value().minusNanos(1);
    return periodFor(new OccurredAt(justBeforeStart), period.granularity());
  }

  @Override
  public List<AccountingPeriod> periodsBetween(
      OccurredAt start, OccurredAt end, PeriodGranularity granularity) {
    Validate.notNull(start, "start must not be null.");
    Validate.notNull(end, "end must not be null.");
    Validate.notNull(granularity, "granularity must not be null.");
    Validate.argument(start.value().isBefore(end.value()), "start must be before end.");
    List<AccountingPeriod> periods = new ArrayList<>();
    AccountingPeriod current = periodFor(start, granularity);
    while (current.start().value().isBefore(end.value())) {
      periods.add(current);
      current = next(current);
    }
    return periods;
  }
}
