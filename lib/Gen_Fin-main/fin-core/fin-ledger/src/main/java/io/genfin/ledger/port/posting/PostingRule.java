package io.genfin.ledger.port.posting;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.posting.PostingIssue;
import java.util.List;

/**
 * One extra check a {@link PostingValidator} runs over a resolved set of {@link PostingEntry} legs,
 * beyond the balance invariant it always enforces itself - e.g. "account is open for posting",
 * "period is not closed". Returns an empty list when nothing is wrong; a validator composes many of
 * these and collects every {@link PostingIssue} rather than stopping at the first one found.
 *
 * <p>This is the basic shape only - concrete rules (period, account-status, ...) arrive in a later
 * stage; for now applications may register their own via this SPI.
 */
public interface PostingRule extends Extension {

  List<PostingIssue> evaluate(List<PostingEntry> entries, PostingContext context);
}
