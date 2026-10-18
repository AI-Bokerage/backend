package io.genfin.ledger.port.journal;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.journal.JournalBuilder;

/**
 * Supplies a fresh {@link JournalBuilder} per call. The extension point exists so a deployment can
 * swap in a builder pre-populated with deployment-specific defaults (e.g. a house journal type or
 * standard attributes) without every call site knowing about it. Mirrors {@code
 * io.genfin.refund.port.refund.RefundBuilderProvider}.
 */
public interface JournalBuilderProvider extends Extension {

  JournalBuilder newBuilder();
}
