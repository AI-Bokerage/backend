package io.genfin.ledger.period;

/**
 * The standard accounting period lifecycle: {@code OPEN} periods accept new postings, {@code
 * CLOSED} periods may still be reopened, {@code LOCKED} periods may not (a correction after locking
 * always takes a reversing entry in a later period, never a reopen), and {@code ARCHIVED} periods
 * are retained for reporting only.
 */
public enum StandardPeriodStatus implements PeriodStatus {
  OPEN,
  CLOSED,
  LOCKED,
  ARCHIVED;

  @Override
  public String code() {
    return name();
  }
}
