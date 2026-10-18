package io.genfin.pricing.quote;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.quote.DefaultQuotePolicy;
import io.genfin.pricing.port.quote.QuotePolicy;
import java.time.Duration;

/**
 * Factory for {@link QuotePolicy} instances. Mirrors {@code
 * io.genfin.pricing.credit.CreditPolicies}.
 */
public final class QuotePolicies {

  private static final Duration DEFAULT_VALIDITY = Duration.ofDays(30);

  private QuotePolicies() {}

  /** The default policy: every offer stays valid for 30 days. */
  public static QuotePolicy standard() {
    return new DefaultQuotePolicy(DEFAULT_VALIDITY);
  }

  /** A policy where every offer stays valid for {@code validity} from when it is built. */
  public static QuotePolicy validFor(Duration validity) {
    return new DefaultQuotePolicy(validity);
  }

  /** Resolves the {@link QuotePolicy} registered in {@code registry}, or {@link #standard()}. */
  public static QuotePolicy from(ExtensionRegistry registry) {
    return registry.find(QuotePolicy.class).orElseGet(QuotePolicies::standard);
  }
}
