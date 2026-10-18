package io.genfin.ledger.internal.posting;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalAttributes;
import io.genfin.ledger.journal.JournalBuilder;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.journal.JournalMetadata;
import io.genfin.ledger.journal.StandardJournalType;
import io.genfin.ledger.port.posting.PostingEngine;
import io.genfin.ledger.port.posting.PostingPolicy;
import io.genfin.ledger.port.posting.PostingValidator;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.posting.PostingIssue;
import io.genfin.ledger.posting.PostingValidationResult;
import io.genfin.refund.reference.ReferenceCollection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link PostingEngine}: resolves a {@link FinancialFact} via the configured {@link
 * PostingPolicy}, rejects the result outright if the configured {@link PostingValidator} finds it
 * invalid, and otherwise builds a system-posted {@link JournalEntry} carrying one {@link
 * JournalLine} per resolved {@link PostingEntry}.
 */
public final class DefaultPostingEngine implements PostingEngine {

  private final PostingPolicy policy;
  private final PostingValidator validator;

  public DefaultPostingEngine(PostingPolicy policy, PostingValidator validator) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
    this.validator = Validate.notNull(validator, "validator must not be null.");
  }

  @Override
  public JournalEntry post(FinancialFact fact, LedgerId ledgerId, PostingContext context) {
    Validate.notNull(fact, "fact must not be null.");
    Validate.notNull(ledgerId, "ledgerId must not be null.");
    Validate.notNull(context, "context must not be null.");

    List<PostingEntry> entries = policy.resolve(fact, context);
    PostingValidationResult result = validator.validate(entries, context);
    if (!result.isValid()) {
      throw new IllegalStateException("Rejected unbalanced posting: " + describe(result));
    }

    List<JournalLine> lines =
        entries.stream().map(entry -> entry.toJournalLine(JournalLineId.generate())).toList();

    return JournalBuilder.newEntry()
        .ledgerId(ledgerId)
        .type(StandardJournalType.STANDARD)
        .lines(lines)
        .references(ReferenceCollection.empty().add(fact.reference()))
        .metadata(new JournalMetadata(fact.metadata()))
        .attributes(JournalAttributes.systemPosted())
        .build();
  }

  private static String describe(PostingValidationResult result) {
    return result.issues().stream().map(PostingIssue::message).collect(Collectors.joining("; "));
  }
}
