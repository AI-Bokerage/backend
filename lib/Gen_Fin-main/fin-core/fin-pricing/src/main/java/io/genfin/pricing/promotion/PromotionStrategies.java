package io.genfin.pricing.promotion;

import io.genfin.api.validation.Validate;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.internal.promotion.RuleBasedPromotionStrategy;
import io.genfin.pricing.port.promotion.PromotionStrategy;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceModifier;
import io.genfin.pricing.price.PriceType;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

/**
 * Factory for {@link PromotionStrategy} instances. {@link #fromRules} is the general-purpose
 * building block; the remaining factory methods are illustrative examples of common campaign shapes
 * (buy-one-get-one, flash sale, weekend sale, early bird, bundle discount) built entirely from
 * {@link PromotionRule}/{@link Promotion}/{@link PromotionEligibility} - none of them is core
 * pipeline logic, and an application is free to ignore all of them and register its own {@link
 * PromotionStrategy} instead. Time-bound shapes take an explicit {@code now} {@link Supplier}
 * rather than reading a system clock directly, so campaigns stay deterministic and testable.
 * Mirrors {@code io.genfin.pricing.discount.DiscountStrategies}.
 */
public final class PromotionStrategies {

  private PromotionStrategies() {}

  /** A strategy backed by a flat list of {@link PromotionRule}s. */
  public static PromotionStrategy fromRules(List<PromotionRule> rules) {
    return new RuleBasedPromotionStrategy(rules);
  }

  /**
   * Example: a flat percentage off once a line's quantity reaches 2 or more (e.g. "buy one, get one
   * 50% off" expressed as a flat rate rather than a free unit).
   */
  public static PromotionStrategy buyOneGetOne(
      String name, PromotionPriority priority, Percentage discount) {
    Validate.notNull(discount, "discount must not be null.");
    PromotionCampaign campaign = PromotionCampaign.of(name, name, priority);
    return fromRules(
        List.of(
            PromotionRule.of(
                campaign,
                percentageOff(campaign, discount),
                (line, context) -> line.quantity() >= 2)));
  }

  /** Example: a flat percentage off while {@code now} falls within the campaign's active window. */
  public static PromotionStrategy flashSale(
      String name,
      PromotionPriority priority,
      Instant startsAt,
      Instant endsAt,
      Percentage discount,
      Supplier<Instant> now) {
    Validate.notNull(discount, "discount must not be null.");
    Validate.notNull(now, "now must not be null.");
    PromotionCampaign campaign = PromotionCampaign.windowed(name, name, priority, startsAt, endsAt);
    return fromRules(
        List.of(
            PromotionRule.of(
                campaign,
                percentageOff(campaign, discount),
                (line, context) -> campaign.isActiveAt(now.get()))));
  }

  /** Example: a flat percentage off whenever {@code now} falls on a Saturday or Sunday (UTC). */
  public static PromotionStrategy weekendSale(
      String name, PromotionPriority priority, Percentage discount, Supplier<Instant> now) {
    Validate.notNull(discount, "discount must not be null.");
    Validate.notNull(now, "now must not be null.");
    PromotionCampaign campaign = PromotionCampaign.of(name, name, priority);
    return fromRules(
        List.of(
            PromotionRule.of(
                campaign,
                percentageOff(campaign, discount),
                (line, context) -> isWeekend(now.get()))));
  }

  /** Example: a flat percentage off for orders placed strictly before {@code cutoff}. */
  public static PromotionStrategy earlyBird(
      String name,
      PromotionPriority priority,
      Instant cutoff,
      Percentage discount,
      Supplier<Instant> now) {
    Validate.notNull(cutoff, "cutoff must not be null.");
    Validate.notNull(discount, "discount must not be null.");
    Validate.notNull(now, "now must not be null.");
    PromotionCampaign campaign = PromotionCampaign.of(name, name, priority);
    return fromRules(
        List.of(
            PromotionRule.of(
                campaign,
                percentageOff(campaign, discount),
                (line, context) -> now.get().isBefore(cutoff))));
  }

  /**
   * Example: a flat percentage off a line whenever {@code companionCatalogId} also appears
   * somewhere in the same {@link io.genfin.pricing.pricing.PricingRequest}.
   */
  public static PromotionStrategy bundleDiscount(
      String name, PromotionPriority priority, CatalogId companionCatalogId, Percentage discount) {
    Validate.notNull(companionCatalogId, "companionCatalogId must not be null.");
    Validate.notNull(discount, "discount must not be null.");
    PromotionCampaign campaign = PromotionCampaign.of(name, name, priority);
    return fromRules(
        List.of(
            PromotionRule.of(
                campaign,
                percentageOff(campaign, discount),
                (line, context) ->
                    context.lines().stream()
                        .anyMatch(other -> other.catalogId().equals(companionCatalogId)))));
  }

  private static boolean isWeekend(Instant instant) {
    DayOfWeek day = instant.atZone(ZoneOffset.UTC).getDayOfWeek();
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
  }

  private static Promotion percentageOff(PromotionCampaign campaign, Percentage discount) {
    return (runningAmount, line, context) ->
        PriceAdjustment.of(
            PriceType.PROMOTION,
            PriceModifier.percentage(discount.negate()),
            runningAmount,
            campaign.description());
  }
}
