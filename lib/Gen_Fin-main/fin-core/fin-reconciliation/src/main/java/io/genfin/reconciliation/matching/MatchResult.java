package io.genfin.reconciliation.matching;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.id.MatchId;
import io.genfin.reconciliation.id.ReconciliationItemId;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of comparing a primary {@link MatchCandidate} against a counterpart (if any) under a
 * named strategy: whether they matched, how confident the match is, and any variance found.
 */
public record MatchResult(
    MatchId id,
    MatchOutcome outcome,
    ReconciliationItemId primary,
    ReconciliationItemId counterpartId,
    double confidence,
    Money variance,
    String strategy,
    List<String> notes)
    implements ValueObject {

  public MatchResult {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(outcome, "outcome must not be null.");
    Validate.notNull(primary, "primary must not be null.");
    Validate.notBlank(strategy, "strategy must not be blank.");
    Validate.required(
        confidence >= 0.0 && confidence <= 1.0, "confidence must be between 0.0 and 1.0.");
    notes = List.copyOf(notes);
  }

  public static MatchResult notMatched(ReconciliationItemId primary, String strategy) {
    return new MatchResult(
        MatchId.generate(),
        MatchOutcome.NOT_MATCHED,
        primary,
        null,
        0.0,
        null,
        strategy,
        List.of());
  }

  public static MatchResult matched(
      ReconciliationItemId primary, ReconciliationItemId counterpart, String strategy) {
    return new MatchResult(
        MatchId.generate(),
        MatchOutcome.MATCHED,
        primary,
        Validate.notNull(counterpart, "counterpart must not be null."),
        1.0,
        null,
        strategy,
        List.of());
  }

  public static MatchResult partiallyMatched(
      ReconciliationItemId primary,
      ReconciliationItemId counterpart,
      double confidence,
      Money variance,
      String strategy,
      String note) {
    return new MatchResult(
        MatchId.generate(),
        MatchOutcome.PARTIALLY_MATCHED,
        primary,
        Validate.notNull(counterpart, "counterpart must not be null."),
        confidence,
        variance,
        strategy,
        note == null ? List.of() : List.of(note));
  }

  public Optional<ReconciliationItemId> counterpart() {
    return Optional.ofNullable(counterpartId);
  }

  public Optional<Money> varianceAmount() {
    return Optional.ofNullable(variance);
  }

  public boolean isMatch() {
    return outcome != MatchOutcome.NOT_MATCHED;
  }
}
