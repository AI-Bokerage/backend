package io.genfin.pricing.promotion;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.PromotionId;
import java.time.Instant;
import java.util.Optional;

/**
 * The named marketing campaign a {@link PromotionRule} evaluates - identity, a human-readable
 * name/description, the {@link PromotionPriority} it wins ties with, and an optional active window
 * ({@code startsAt}/{@code endsAt}) illustrative time-bound shapes (flash sale, weekend sale, early
 * bird) read to decide eligibility. Carries no arithmetic of its own - that is {@link Promotion}'s
 * concern - so the same campaign metadata can back campaigns with different eligibility/adjustment
 * combinations.
 */
public record PromotionCampaign(
    PromotionId id,
    String name,
    String description,
    PromotionPriority priority,
    Optional<Instant> startsAt,
    Optional<Instant> endsAt)
    implements ValueObject {

  public PromotionCampaign {
    Validate.notNull(id, "id must not be null.");
    Validate.notBlank(name, "name must not be blank.");
    Validate.notBlank(description, "description must not be blank.");
    Validate.notNull(priority, "priority must not be null.");
    Validate.notNull(startsAt, "startsAt must not be null.");
    Validate.notNull(endsAt, "endsAt must not be null.");
  }

  /**
   * A campaign with no active window - eligibility is driven entirely by {@link
   * PromotionEligibility}, not by time.
   */
  public static PromotionCampaign of(String name, String description, PromotionPriority priority) {
    return new PromotionCampaign(
        PromotionId.generate(), name, description, priority, Optional.empty(), Optional.empty());
  }

  /** A campaign active only within {@code [startsAt, endsAt]}. */
  public static PromotionCampaign windowed(
      String name,
      String description,
      PromotionPriority priority,
      Instant startsAt,
      Instant endsAt) {
    Validate.notNull(startsAt, "startsAt must not be null.");
    Validate.notNull(endsAt, "endsAt must not be null.");
    return new PromotionCampaign(
        PromotionId.generate(),
        name,
        description,
        priority,
        Optional.of(startsAt),
        Optional.of(endsAt));
  }

  /** Whether {@code instant} falls within this campaign's active window, if it has one. */
  public boolean isActiveAt(Instant instant) {
    Validate.notNull(instant, "instant must not be null.");
    return startsAt.map(start -> !instant.isBefore(start)).orElse(true)
        && endsAt.map(end -> !instant.isAfter(end)).orElse(true);
  }
}
