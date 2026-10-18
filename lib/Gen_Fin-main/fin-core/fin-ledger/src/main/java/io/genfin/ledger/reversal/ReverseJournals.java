package io.genfin.ledger.reversal;

import io.genfin.ledger.internal.reversal.DefaultReverseJournal;
import io.genfin.ledger.port.reversal.ReversalPolicy;
import io.genfin.ledger.port.reversal.ReverseJournal;
import io.genfin.ledger.port.reversal.ReversePosting;

/**
 * Factory for the default {@link ReverseJournal} implementation, mirroring {@code PostingEngines}.
 */
public final class ReverseJournals {

  private ReverseJournals() {}

  /**
   * An engine driven by {@link ReversalPolicies#standard()} and {@link ReversePostings#standard()}.
   */
  public static ReverseJournal standard() {
    return of(ReversalPolicies.standard(), ReversePostings.standard());
  }

  public static ReverseJournal of(ReversalPolicy policy, ReversePosting reversePosting) {
    return new DefaultReverseJournal(policy, reversePosting);
  }
}
