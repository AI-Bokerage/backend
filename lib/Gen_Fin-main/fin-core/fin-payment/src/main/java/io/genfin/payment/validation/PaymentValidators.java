package io.genfin.payment.validation;

import io.genfin.payment.internal.validation.DefaultPaymentValidator;
import io.genfin.payment.internal.validation.rules.CaptureExceedsAuthorizationRule;
import io.genfin.payment.internal.validation.rules.CurrencyMismatchRule;
import io.genfin.payment.internal.validation.rules.DuplicateIdempotencyKeyRule;
import io.genfin.payment.internal.validation.rules.ExpiredSessionRule;
import io.genfin.payment.internal.validation.rules.InvalidCaptureRule;
import io.genfin.payment.internal.validation.rules.NegativeAmountRule;
import io.genfin.payment.internal.validation.rules.UnsupportedPaymentMethodRule;
import io.genfin.payment.method.PaymentMethodRegistries;
import io.genfin.payment.port.method.PaymentMethodRegistry;
import io.genfin.payment.port.validation.PaymentValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link PaymentValidator}s. {@link #standard()} carries every default rule; extend via
 * {@link #withRules}.
 */
public final class PaymentValidators {

  private PaymentValidators() {}

  public static List<ValidationRule> defaultRules(PaymentMethodRegistry methodRegistry) {
    return List.of(
        new NegativeAmountRule(),
        new CurrencyMismatchRule(),
        new InvalidCaptureRule(),
        new CaptureExceedsAuthorizationRule(),
        new ExpiredSessionRule(),
        new DuplicateIdempotencyKeyRule(),
        new UnsupportedPaymentMethodRule(methodRegistry));
  }

  public static PaymentValidator standard() {
    return new DefaultPaymentValidator(
        defaultRules(
            PaymentMethodRegistries.withProvider(PaymentMethodRegistries.standardCatalog())));
  }

  public static PaymentValidator withRules(
      PaymentMethodRegistry methodRegistry, List<ValidationRule> additionalRules) {
    List<ValidationRule> combined = new ArrayList<>(defaultRules(methodRegistry));
    combined.addAll(additionalRules);
    return new DefaultPaymentValidator(combined);
  }
}
