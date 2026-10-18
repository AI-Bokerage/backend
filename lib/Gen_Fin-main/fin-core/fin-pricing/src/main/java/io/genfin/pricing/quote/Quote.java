package io.genfin.pricing.quote;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.PricingCalculator;
import io.genfin.pricing.id.PricingResultId;
import io.genfin.pricing.id.QuoteId;
import io.genfin.pricing.pricing.PricingMetadata;
import io.genfin.pricing.pricing.PricingResult;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable commercial offer built from a {@link PricingResult} - explicitly NOT an invoice:
 * fin-pricing has no invoice-related dependency to reference, and a Quote never captures a payment
 * or posts an accounting entry. Once issued, a Quote is never mutated in place; every transition
 * (issue, accept, reject, expire, revise) returns a new {@code Quote}, the later ones carrying a
 * bumped {@link QuoteVersion}, mirroring how a repriced {@link
 * io.genfin.pricing.pricing.PricingRequest} yields a new {@link PricingResult} rather than mutating
 * the old one.
 */
public record Quote(
    QuoteId id,
    PricingResultId pricingResultId,
    QuoteVersion version,
    QuoteStatus status,
    List<QuoteItem> items,
    QuoteSummary summary,
    QuoteExpiration expiration,
    PricingMetadata metadata,
    Instant issuedAt)
    implements ValueObject {

  private static final Map<QuoteStatus, Set<QuoteStatus>> ALLOWED_TRANSITIONS =
      allowedTransitions();

  public Quote {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(pricingResultId, "pricingResultId must not be null.");
    Validate.notNull(version, "version must not be null.");
    Validate.notNull(status, "status must not be null.");
    Validate.notNull(items, "items must not be null.");
    Validate.argument(!items.isEmpty(), "items must not be empty.");
    items = List.copyOf(items);
    Validate.notNull(summary, "summary must not be null.");
    Validate.notNull(expiration, "expiration must not be null.");
    Validate.notNull(metadata, "metadata must not be null.");
    Validate.notNull(issuedAt, "issuedAt must not be null.");
  }

  /** A freshly built {@code DRAFT} offer for {@code items}, valid until {@code expiration}. */
  public static Quote draft(
      PricingResultId pricingResultId, List<QuoteItem> items, QuoteExpiration expiration) {
    Validate.notNull(items, "items must not be null.");
    Validate.argument(!items.isEmpty(), "items must not be empty.");
    return new Quote(
        QuoteId.generate(),
        pricingResultId,
        QuoteVersion.initial(),
        QuoteStatus.DRAFT,
        items,
        summarize(items),
        expiration,
        PricingMetadata.empty(),
        Instant.now());
  }

  /** {@code DRAFT -> ISSUED}: the offer is presented to the customer. */
  public Quote issue() {
    return transitionTo(QuoteStatus.ISSUED);
  }

  /** {@code ISSUED -> ACCEPTED}: rejected if the offer has already expired. */
  public Quote accept() {
    Validate.state(!isExpiredAt(Instant.now()), "Cannot accept an expired quote.");
    return transitionTo(QuoteStatus.ACCEPTED);
  }

  /** {@code ISSUED -> REJECTED}: the customer declined the offer. */
  public Quote reject() {
    return transitionTo(QuoteStatus.REJECTED);
  }

  /** {@code DRAFT} or {@code ISSUED -> EXPIRED}. */
  public Quote expire() {
    return transitionTo(QuoteStatus.EXPIRED);
  }

  /**
   * Supersedes this offer with a new {@code DRAFT} revision carrying the next {@link QuoteVersion}
   * - the Quote Engine's counterpart to a {@link io.genfin.pricing.pricing.PricingRequest} being
   * repriced into a new {@link PricingResult}.
   */
  public Quote revise(List<QuoteItem> newItems, QuoteExpiration newExpiration) {
    Validate.notNull(newItems, "newItems must not be null.");
    Validate.argument(!newItems.isEmpty(), "newItems must not be empty.");
    Validate.notNull(newExpiration, "newExpiration must not be null.");
    return new Quote(
        id,
        pricingResultId,
        version.next(),
        QuoteStatus.DRAFT,
        newItems,
        summarize(newItems),
        newExpiration,
        metadata,
        Instant.now());
  }

  public Quote withMetadata(PricingMetadata newMetadata) {
    Validate.notNull(newMetadata, "newMetadata must not be null.");
    return new Quote(
        id, pricingResultId, version, status, items, summary, expiration, newMetadata, issuedAt);
  }

  public boolean isExpiredAt(Instant instant) {
    return expiration.isExpiredAt(instant);
  }

  private Quote transitionTo(QuoteStatus next) {
    Validate.state(
        ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(next),
        "Cannot move quote from " + status + " to " + next + ".");
    return new Quote(
        id, pricingResultId, version, next, items, summary, expiration, metadata, issuedAt);
  }

  private static QuoteSummary summarize(List<QuoteItem> items) {
    return QuoteSummary.of(
        PricingCalculator.summarize(
            items.stream().map(QuoteItem::price).toList(),
            items.get(0).price().amount().currency()));
  }

  private static Map<QuoteStatus, Set<QuoteStatus>> allowedTransitions() {
    Map<QuoteStatus, Set<QuoteStatus>> transitions = new EnumMap<>(QuoteStatus.class);
    transitions.put(QuoteStatus.DRAFT, Set.of(QuoteStatus.ISSUED, QuoteStatus.EXPIRED));
    transitions.put(
        QuoteStatus.ISSUED,
        Set.of(QuoteStatus.ACCEPTED, QuoteStatus.REJECTED, QuoteStatus.EXPIRED));
    transitions.put(QuoteStatus.ACCEPTED, Set.of());
    transitions.put(QuoteStatus.REJECTED, Set.of());
    transitions.put(QuoteStatus.EXPIRED, Set.of());
    return Map.copyOf(transitions);
  }
}
