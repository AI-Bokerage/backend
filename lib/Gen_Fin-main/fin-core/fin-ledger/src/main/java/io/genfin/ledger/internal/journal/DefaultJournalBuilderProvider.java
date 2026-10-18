package io.genfin.ledger.internal.journal;

import io.genfin.ledger.journal.JournalBuilder;
import io.genfin.ledger.port.journal.JournalBuilderProvider;

/**
 * Default {@link JournalBuilderProvider}: a plain, unconfigured {@link JournalBuilder} each call.
 */
public final class DefaultJournalBuilderProvider implements JournalBuilderProvider {

  @Override
  public JournalBuilder newBuilder() {
    return JournalBuilder.newEntry();
  }
}
