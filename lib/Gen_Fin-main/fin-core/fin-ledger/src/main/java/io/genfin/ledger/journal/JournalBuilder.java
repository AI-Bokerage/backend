package io.genfin.ledger.journal;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.lifecycle.LedgerEvent;
import io.genfin.ledger.lifecycle.LedgerLifecycles;
import io.genfin.ledger.lifecycle.LedgerStatus;
import io.genfin.refund.reference.ReferenceCollection;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link JournalEntry} aggregates. Preferred over the canonical constructor for readability
 * at call sites. Mirrors {@code io.genfin.refund.refund.RefundBuilder}.
 */
public final class JournalBuilder {

  private JournalEntryId id;
  private LedgerId ledgerId;
  private JournalType type = StandardJournalType.STANDARD;
  private final List<JournalLine> lines = new ArrayList<>();
  private StateMachine<LedgerStatus, LedgerEvent> lifecycle;
  private ReferenceCollection references = ReferenceCollection.empty();
  private JournalMetadata metadata = JournalMetadata.empty();
  private JournalAttributes attributes = JournalAttributes.standard();

  private JournalBuilder() {}

  public static JournalBuilder newEntry() {
    return new JournalBuilder();
  }

  public JournalBuilder id(JournalEntryId id) {
    this.id = id;
    return this;
  }

  public JournalBuilder ledgerId(LedgerId ledgerId) {
    this.ledgerId = ledgerId;
    return this;
  }

  public JournalBuilder type(JournalType type) {
    this.type = type;
    return this;
  }

  public JournalBuilder line(JournalLine line) {
    this.lines.add(line);
    return this;
  }

  public JournalBuilder lines(List<JournalLine> lines) {
    this.lines.clear();
    this.lines.addAll(lines);
    return this;
  }

  public JournalBuilder lifecycle(StateMachine<LedgerStatus, LedgerEvent> lifecycle) {
    this.lifecycle = lifecycle;
    return this;
  }

  public JournalBuilder references(ReferenceCollection references) {
    this.references = references;
    return this;
  }

  public JournalBuilder metadata(JournalMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public JournalBuilder attributes(JournalAttributes attributes) {
    this.attributes = attributes;
    return this;
  }

  public JournalEntry build() {
    StateMachine<LedgerStatus, LedgerEvent> resolvedLifecycle =
        lifecycle != null ? lifecycle : LedgerLifecycles.created();
    return new JournalEntry(
        id == null ? JournalEntryId.generate() : id,
        ledgerId,
        type,
        List.copyOf(lines),
        resolvedLifecycle,
        references,
        metadata,
        attributes);
  }
}
