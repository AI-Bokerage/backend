package io.genfin.ledger.posting;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import java.time.Instant;
import java.util.Map;

/**
 * Everything a {@link io.genfin.ledger.port.posting.PostingStrategy} or {@link
 * io.genfin.ledger.port.posting.PostingRule} may need beyond the {@link
 * io.genfin.ledger.fact.FinancialFact} itself, mirroring {@code
 * io.genfin.reconciliation.rule.RuleContext} / {@code
 * io.genfin.refund.validation.ValidationContext}.
 */
public record PostingContext(Instant asOf, Map<String, String> metadata) implements ValueObject {

  public PostingContext {
    Validate.notNull(asOf, "asOf must not be null.");
    metadata = CollectionUtils.immutableMap(metadata);
  }

  public static PostingContext at(Instant asOf) {
    return new PostingContext(asOf, Map.of());
  }

  public PostingContext withMetadata(Map<String, String> metadata) {
    return new PostingContext(asOf, metadata);
  }
}
