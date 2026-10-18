package io.genfin.ledger.port.posting;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.posting.PostingValidationResult;
import java.util.List;

/**
 * Validates a resolved set of {@link PostingEntry} legs before the {@link PostingEngine} converts
 * them into a {@link io.genfin.ledger.journal.JournalEntry}. Every implementation MUST reject an
 * unbalanced posting (total debit != total credit) - this is a hard invariant of Gen-Fin's
 * double-entry model, not a soft check a deployment may opt out of; the additional, fully
 * configurable checks a deployment layers on top are expressed as {@link PostingRule}s.
 */
public interface PostingValidator extends Extension {

  PostingValidationResult validate(List<PostingEntry> entries, PostingContext context);
}
