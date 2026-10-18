package io.genfin.ledger.period;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.exception.StateTransitionException;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountingPeriodId;
import java.util.Map;

/**
 * One bounded window - {@code start} (inclusive) to {@code end} (exclusive) - a {@link
 * io.genfin.ledger.port.period.PeriodCalculator} carves out of a {@link FiscalYear} at a given
 * {@link PeriodGranularity}. Immutable like every other value in Gen-Fin's ledger model: closing,
 * locking or archiving a period never mutates it in place, each returns a new {@code
 * AccountingPeriod} carrying the new {@link PeriodStatus} - mirroring how journal entries are never
 * deleted, only superseded by new state.
 */
public record AccountingPeriod(
    AccountingPeriodId id,
    PeriodGranularity granularity,
    FiscalYear fiscalYear,
    OccurredAt start,
    OccurredAt end,
    PeriodStatus status)
    implements ValueObject {

  public AccountingPeriod {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(granularity, "granularity must not be null.");
    Validate.notNull(fiscalYear, "fiscalYear must not be null.");
    Validate.notNull(start, "start must not be null.");
    Validate.notNull(end, "end must not be null.");
    Validate.notNull(status, "status must not be null.");
    Validate.argument(start.value().isBefore(end.value()), "start must be before end.");
  }

  /** Whether {@code instant} falls within {@code [start, end)}. */
  public boolean contains(OccurredAt instant) {
    Validate.notNull(instant, "instant must not be null.");
    return !instant.value().isBefore(start.value()) && instant.value().isBefore(end.value());
  }

  /** Whether this period currently accepts new postings. */
  public boolean isOpen() {
    return status.code().equals(StandardPeriodStatus.OPEN.code());
  }

  /** Closes an {@code OPEN} period. A closed period may still be {@link #reopen()}ed. */
  public AccountingPeriod close() {
    requireStatus(StandardPeriodStatus.OPEN, "close");
    return withStatus(StandardPeriodStatus.CLOSED);
  }

  /** Reopens a {@code CLOSED} period, e.g. to admit a late-arriving fact before it is locked. */
  public AccountingPeriod reopen() {
    requireStatus(StandardPeriodStatus.CLOSED, "reopen");
    return withStatus(StandardPeriodStatus.OPEN);
  }

  /** Locks a {@code CLOSED} period. Once locked, a correction must reverse into a later period. */
  public AccountingPeriod lock() {
    requireStatus(StandardPeriodStatus.CLOSED, "lock");
    return withStatus(StandardPeriodStatus.LOCKED);
  }

  /** Archives a {@code LOCKED} period for long-term reporting retention. */
  public AccountingPeriod archive() {
    requireStatus(StandardPeriodStatus.LOCKED, "archive");
    return withStatus(StandardPeriodStatus.ARCHIVED);
  }

  private void requireStatus(StandardPeriodStatus required, String action) {
    if (!status.code().equals(required.code())) {
      throw new StateTransitionException(
          "cannot " + action + " a period that is " + status.code() + ".",
          Map.of("periodId", id.value(), "status", status.code(), "action", action));
    }
  }

  private AccountingPeriod withStatus(PeriodStatus newStatus) {
    return new AccountingPeriod(id, granularity, fiscalYear, start, end, newStatus);
  }
}
