package io.genfin.ledger.journal;

import io.genfin.api.domain.Entity;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.TransitionResult;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.lifecycle.LedgerEvent;
import io.genfin.ledger.lifecycle.LedgerLifecycles;
import io.genfin.ledger.lifecycle.LedgerStatus;
import io.genfin.ledger.lifecycle.StandardLedgerEvent;
import io.genfin.money.money.Money;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.reference.ReferenceCollection;
import java.util.List;

/**
 * A single journal entry belonging to a {@link io.genfin.ledger.ledger.Ledger}, made up of the
 * {@link JournalLine}s it double-entry-posts across accounts. Total debits equal total credits by
 * construction - {@link #JournalEntry(JournalEntryId, LedgerId, JournalType, List)} rejects any
 * unbalanced or single-sided set of lines, it is not a convention callers must remember to honour.
 *
 * <p>A ledger holds many journal entries that each progress through the posting lifecycle
 * independently, so the lifecycle is carried here rather than on the ledger aggregate itself.
 * Lifecycle transitions are driven entirely by the SPI-replaceable {@link
 * io.genfin.ledger.port.lifecycle.LedgerLifecycleProvider}; this entity holds no transition table
 * of its own. Corrections are never applied by mutating or removing a past state or line - {@link
 * #reverse()} and {@link #adjust()} are additional forward transitions recorded onto the
 * append-only {@link JournalHistory}, the lines themselves are never changed after construction.
 *
 * <p>Mirrors {@code io.genfin.refund.refund.Refund}: the entry never owns
 * Invoice/Payment/Refund/Settlement/BankTransaction records directly, only generic {@link
 * Reference}s to them via {@link #references()}.
 */
public final class JournalEntry extends Entity<JournalEntryId> {

  private final LedgerId ledgerId;
  private final JournalType type;
  private final List<JournalLine> lines;
  private final StateMachine<LedgerStatus, LedgerEvent> lifecycle;

  private JournalHistory history;
  private JournalVersion version;
  private ReferenceCollection references;
  private JournalMetadata metadata;
  private JournalAttributes attributes;

  public JournalEntry(
      JournalEntryId id, LedgerId ledgerId, JournalType type, List<JournalLine> lines) {
    this(
        id,
        ledgerId,
        type,
        lines,
        LedgerLifecycles.created(),
        ReferenceCollection.empty(),
        JournalMetadata.empty(),
        JournalAttributes.standard());
  }

  public JournalEntry(
      JournalEntryId id,
      LedgerId ledgerId,
      JournalType type,
      List<JournalLine> lines,
      StateMachine<LedgerStatus, LedgerEvent> lifecycle,
      ReferenceCollection references,
      JournalMetadata metadata,
      JournalAttributes attributes) {
    super(id);
    this.ledgerId = Validate.notNull(ledgerId, "ledgerId must not be null.");
    this.type = Validate.notNull(type, "type must not be null.");
    this.lines = requireBalanced(CollectionUtils.immutableList(lines));
    this.lifecycle = Validate.notNull(lifecycle, "lifecycle must not be null.");
    this.references = Validate.notNull(references, "references must not be null.");
    this.metadata = Validate.notNull(metadata, "metadata must not be null.");
    this.attributes = Validate.notNull(attributes, "attributes must not be null.");
    this.history = JournalHistory.initial(lifecycle.currentState());
    this.version = JournalVersion.initial();
  }

  private static List<JournalLine> requireBalanced(List<JournalLine> lines) {
    Validate.argument(lines.size() >= 2, "a journal entry needs at least two lines.");
    Money totalDebit = null;
    Money totalCredit = null;
    for (JournalLine line : lines) {
      totalDebit = totalDebit == null ? line.debit() : totalDebit.add(line.debit());
      totalCredit = totalCredit == null ? line.credit() : totalCredit.add(line.credit());
    }
    Validate.argument(
        totalDebit.equals(totalCredit),
        "unbalanced journal entry: total debits ("
            + totalDebit
            + ") must equal total credits ("
            + totalCredit
            + ").");
    return lines;
  }

  public LedgerId ledgerId() {
    return ledgerId;
  }

  public JournalType type() {
    return type;
  }

  public List<JournalLine> lines() {
    return lines;
  }

  public LedgerStatus status() {
    return lifecycle.currentState();
  }

  public JournalHistory history() {
    return history;
  }

  public JournalVersion version() {
    return version;
  }

  public ReferenceCollection references() {
    return references;
  }

  public JournalMetadata metadata() {
    return metadata;
  }

  public JournalAttributes attributes() {
    return attributes;
  }

  public void addReference(Reference reference) {
    references = references.add(Validate.notNull(reference, "reference must not be null."));
  }

  public void updateMetadata(JournalMetadata metadata) {
    this.metadata = Validate.notNull(metadata, "metadata must not be null.");
  }

  public void validate() {
    fire(StandardLedgerEvent.VALIDATE);
  }

  public void post() {
    fire(StandardLedgerEvent.POST);
  }

  public void settle() {
    fire(StandardLedgerEvent.SETTLE);
  }

  public void reverse() {
    fire(StandardLedgerEvent.REVERSE);
  }

  public void adjust() {
    fire(StandardLedgerEvent.ADJUST);
  }

  public void archive() {
    fire(StandardLedgerEvent.ARCHIVE);
  }

  public void fail() {
    fire(StandardLedgerEvent.FAIL);
  }

  public void retry() {
    fire(StandardLedgerEvent.RETRY);
  }

  private void fire(LedgerEvent event) {
    TransitionResult<LedgerStatus> result = lifecycle.fire(event);
    if (!result.isAllowed()) {
      throw new IllegalStateException(
          "Cannot apply "
              + event.code()
              + " while journal entry is "
              + lifecycle.currentState().code()
              + ".");
    }
    history = history.append(result.currentState());
    version = version.next();
  }
}
