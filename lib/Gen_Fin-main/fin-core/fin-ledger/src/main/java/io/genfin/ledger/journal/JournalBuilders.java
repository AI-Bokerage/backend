package io.genfin.ledger.journal;

import io.genfin.ledger.internal.journal.DefaultJournalBuilderProvider;
import io.genfin.ledger.port.journal.JournalBuilderProvider;

/** Factory for {@link JournalBuilderProvider}s, mirroring {@code RefundBuilders}. */
public final class JournalBuilders {

  private static final JournalBuilderProvider STANDARD = new DefaultJournalBuilderProvider();

  private JournalBuilders() {}

  public static JournalBuilderProvider standard() {
    return STANDARD;
  }
}
