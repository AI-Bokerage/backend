package io.genfin.dunning.config;

/**
 * Factory for the default {@link DunningConfiguration}. Every sub-configuration starts from its own
 * "standard"/empty default - fin-dunning ships no built-in retry interval, day count, weekend rule,
 * holiday, channel, or escalation ladder, so a consuming application registers its own before this
 * configuration governs anything real. Mirrors {@code
 * io.genfin.pricing.config.PricingConfigurations}.
 */
public final class DunningConfigurations {

  private DunningConfigurations() {}

  public static DunningConfiguration standard() {
    return DunningConfiguration.builder().build();
  }
}
