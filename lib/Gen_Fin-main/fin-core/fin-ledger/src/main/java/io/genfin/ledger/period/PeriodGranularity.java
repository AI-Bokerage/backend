package io.genfin.ledger.period;

/**
 * The *bucket size* of an {@link AccountingPeriod} (daily, monthly, quarterly, yearly, ...).
 * Mirrors {@code io.genfin.ledger.journal.JournalType}: a structural taxonomy, not a business
 * concept, so it needs no registry - just an interface a deployment may implement beyond {@link
 * StandardPeriodGranularity} if its fiscal calendar buckets periods differently (e.g. a 4-4-5
 * retail calendar or weekly periods). A custom granularity only takes effect once a matching {@link
 * io.genfin.ledger.port.period.PeriodPolicy} is registered to compute its boundaries - Gen-Fin
 * never hardcodes one fiscal calendar as the only option.
 */
public interface PeriodGranularity {

  String code();
}
