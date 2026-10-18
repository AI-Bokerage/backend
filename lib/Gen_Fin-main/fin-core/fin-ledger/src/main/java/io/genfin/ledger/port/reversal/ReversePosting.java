package io.genfin.ledger.port.reversal;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.posting.PostingEntry;
import java.util.List;

/**
 * Turns one posted {@link JournalLine} into the opposite-side {@link PostingEntry} a reversal posts
 * for it - a debit line reverses to a credit of the same amount/account and vice versa. This is the
 * mechanical swap the hard "total debit == total credit" invariant relies on: reversing every line
 * of a balanced entry always yields another balanced set.
 */
public interface ReversePosting extends Extension {

  PostingEntry reverse(JournalLine line);

  default List<PostingEntry> reverseAll(List<JournalLine> lines) {
    return lines.stream().map(this::reverse).toList();
  }
}
