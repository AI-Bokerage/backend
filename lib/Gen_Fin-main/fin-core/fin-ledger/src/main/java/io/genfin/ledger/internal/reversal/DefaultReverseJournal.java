package io.genfin.ledger.internal.reversal;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.exception.CoreErrorCode;
import io.genfin.api.exception.GenFinException;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.ledger.journal.JournalAttributes;
import io.genfin.ledger.journal.JournalBuilder;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.journal.JournalMetadata;
import io.genfin.ledger.journal.StandardJournalType;
import io.genfin.ledger.port.reversal.ReversalPolicy;
import io.genfin.ledger.port.reversal.ReverseJournal;
import io.genfin.ledger.port.reversal.ReversePosting;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.posting.PostingIssue;
import io.genfin.ledger.reversal.Adjustment;
import io.genfin.ledger.reversal.Correction;
import io.genfin.ledger.reversal.Reversal;
import io.genfin.ledger.reversal.ReversalReason;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.reference.ReferenceCollection;
import io.genfin.refund.reference.StandardReferenceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link ReverseJournal}: rejects a reversal/adjustment outright if the configured {@link
 * ReversalPolicy} objects, otherwise fires the original entry's own lifecycle event and builds the
 * new entry alongside it. The original {@link JournalEntry}'s lines are never read except to derive
 * the reversing entry's lines - they are never rewritten or removed.
 */
public final class DefaultReverseJournal implements ReverseJournal {

  private static final StandardReferenceType JOURNAL_ENTRY_REFERENCE =
      StandardReferenceType.of("JOURNAL_ENTRY");

  private final ReversalPolicy policy;
  private final ReversePosting reversePosting;

  public DefaultReverseJournal(ReversalPolicy policy, ReversePosting reversePosting) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
    this.reversePosting = Validate.notNull(reversePosting, "reversePosting must not be null.");
  }

  @Override
  public Reversal reverse(
      JournalEntry original, ReversalReason reason, String memo, Instant reversedAt) {
    Validate.notNull(original, "original must not be null.");
    Validate.notNull(reason, "reason must not be null.");
    Validate.notNull(reversedAt, "reversedAt must not be null.");

    requirePermitted(original, reason);
    original.reverse();

    List<JournalLine> lines = toLines(reversePosting.reverseAll(original.lines()));
    JournalEntry reversingEntry =
        buildEntry(original, lines, StandardJournalType.REVERSAL, reason, memo);

    return new Reversal(original, reversingEntry, reason, memo, new OccurredAt(reversedAt));
  }

  @Override
  public Adjustment adjust(
      JournalEntry original,
      List<PostingEntry> adjustingEntries,
      ReversalReason reason,
      String memo,
      Instant adjustedAt) {
    Validate.notNull(original, "original must not be null.");
    Validate.notNull(adjustingEntries, "adjustingEntries must not be null.");
    Validate.notNull(reason, "reason must not be null.");
    Validate.notNull(adjustedAt, "adjustedAt must not be null.");

    requirePermitted(original, reason);
    original.adjust();

    List<JournalLine> lines = toLines(adjustingEntries);
    JournalEntry adjustingEntry =
        buildEntry(original, lines, StandardJournalType.ADJUSTMENT, reason, memo);

    return new Adjustment(original, adjustingEntry, reason, memo, new OccurredAt(adjustedAt));
  }

  @Override
  public Correction correct(
      JournalEntry original,
      List<PostingEntry> correctedEntries,
      ReversalReason reason,
      String memo,
      Instant correctedAt) {
    Validate.notNull(correctedEntries, "correctedEntries must not be null.");

    Reversal reversal = reverse(original, reason, memo, correctedAt);

    List<JournalLine> lines = toLines(correctedEntries);
    JournalEntry correctingEntry =
        buildEntry(original, lines, StandardJournalType.STANDARD, reason, memo);

    return new Correction(reversal, correctingEntry);
  }

  private void requirePermitted(JournalEntry original, ReversalReason reason) {
    Optional<PostingIssue> violation = policy.check(original, reason);
    if (violation.isPresent()) {
      throw new GenFinException(CoreErrorCode.VALIDATION_FAILED, violation.get().message());
    }
  }

  private static List<JournalLine> toLines(List<PostingEntry> entries) {
    return entries.stream().map(entry -> entry.toJournalLine(JournalLineId.generate())).toList();
  }

  private static JournalEntry buildEntry(
      JournalEntry original,
      List<JournalLine> lines,
      StandardJournalType type,
      ReversalReason reason,
      String memo) {
    Reference originalReference = Reference.of(JOURNAL_ENTRY_REFERENCE, original.id().value());
    JournalMetadata metadata =
        new JournalMetadata(
            Map.of("reversalReason", reason.code(), "memo", memo == null ? "" : memo));

    return JournalBuilder.newEntry()
        .ledgerId(original.ledgerId())
        .type(type)
        .lines(lines)
        .references(ReferenceCollection.empty().add(originalReference))
        .metadata(metadata)
        .attributes(JournalAttributes.standard())
        .build();
  }
}
