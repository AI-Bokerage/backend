package io.genfin.ledger.port.validation;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationResult;

/**
 * Validates a {@link JournalEntry} - a composable, SPI-replaceable check made up of {@link
 * io.genfin.ledger.validation.ValidationRule}s. Distinct from {@link
 * io.genfin.ledger.port.posting.PostingValidator}: that validator runs before a {@link
 * JournalEntry} exists, over the raw {@link io.genfin.ledger.posting.PostingEntry} legs a {@link
 * io.genfin.ledger.port.posting.PostingStrategy} produced; this one runs afterwards, over the
 * constructed entry itself, and additionally covers Chart of Accounts, Accounting Period and
 * posting-rule-match concerns the posting validator has no visibility into.
 */
public interface JournalValidator extends Extension {

  ValidationResult validate(JournalEntry entry, ValidationContext context);
}
