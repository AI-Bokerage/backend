package io.genfin.ledger.internal.reversal;

import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.port.reversal.ReversalPolicy;
import io.genfin.ledger.port.reversal.ReversalReasonRegistry;
import io.genfin.ledger.posting.PostingIssue;
import io.genfin.ledger.reversal.ReversalReason;
import java.util.Optional;

/**
 * Permits a reversal/adjustment only when {@code reason} is registered in the given {@link
 * ReversalReasonRegistry} and the original entry's own {@link
 * io.genfin.ledger.journal.JournalAttributes#reversible()} flag allows it. Which specific lifecycle
 * transition is legal from the entry's current state is left to the entry's own {@link
 * io.genfin.ledger.port.lifecycle.LedgerLifecycleProvider} - this policy does not re-derive it.
 */
public final class DefaultReversalPolicy implements ReversalPolicy {

  private final ReversalReasonRegistry registry;

  public DefaultReversalPolicy(ReversalReasonRegistry registry) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
  }

  @Override
  public Optional<PostingIssue> check(JournalEntry original, ReversalReason reason) {
    Validate.notNull(original, "original must not be null.");
    Validate.notNull(reason, "reason must not be null.");

    if (registry.find(reason).isEmpty()) {
      return Optional.of(
          PostingIssue.of(
              "UNSUPPORTED_REVERSAL_REASON",
              "Reversal reason " + reason.code() + " is not permitted.",
              Severity.ERROR));
    }
    if (!original.attributes().reversible()) {
      return Optional.of(
          PostingIssue.of(
              "ENTRY_NOT_REVERSIBLE",
              "Journal entry " + original.id() + " is marked as not reversible.",
              Severity.ERROR));
    }
    return Optional.empty();
  }
}
