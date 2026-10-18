package io.genfin.ledger.journal;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.lifecycle.LedgerLifecycles;
import io.genfin.ledger.lifecycle.StandardLedgerStatus;
import io.genfin.ledger.port.lifecycle.LedgerLifecycleProvider;
import io.genfin.refund.reference.ReferenceCollection;
import java.util.List;

/**
 * Creates {@link JournalEntry}s wired to the {@link LedgerLifecycleProvider} registered in an
 * {@link ExtensionRegistry}, falling back to {@link LedgerLifecycles#standard()}. Preferred over
 * {@link JournalBuilder} directly whenever the lifecycle should come from the deployment's
 * configured extensions rather than the built-in default. Mirrors {@code
 * io.genfin.refund.refund.RefundFactory}.
 */
public final class JournalFactory {

  private JournalFactory() {}

  public static JournalEntry create(
      ExtensionRegistry registry,
      LedgerId ledgerId,
      JournalType type,
      List<JournalLine> lines,
      ReferenceCollection references,
      JournalMetadata metadata,
      JournalAttributes attributes) {
    LedgerLifecycleProvider lifecycleProvider =
        registry.find(LedgerLifecycleProvider.class).orElseGet(LedgerLifecycles::standard);
    return JournalBuilder.newEntry()
        .ledgerId(ledgerId)
        .type(type)
        .lines(lines)
        .references(references)
        .metadata(metadata)
        .attributes(attributes)
        .lifecycle(lifecycleProvider.create(StandardLedgerStatus.CREATED))
        .build();
  }
}
