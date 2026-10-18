package io.genfin.refund.validation;

import io.genfin.refund.internal.validation.DefaultRefundValidator;
import io.genfin.refund.internal.validation.rules.DuplicateRefundReferenceRule;
import io.genfin.refund.internal.validation.rules.ExpiredRefundWindowRule;
import io.genfin.refund.internal.validation.rules.InvalidRefundReasonRule;
import io.genfin.refund.internal.validation.rules.NegativeRefundAmountRule;
import io.genfin.refund.internal.validation.rules.RefundCurrencyMismatchRule;
import io.genfin.refund.internal.validation.rules.RefundExceedsBalanceRule;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import io.genfin.refund.port.validation.RefundValidator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link RefundValidator}s. {@link #standard} carries every default rule; extend via
 * {@link #withRules}.
 */
public final class RefundValidators {

  /** No sensible universal default exists; callers pick a window via {@link #defaultRules}. */
  private static final Duration DEFAULT_WINDOW = Duration.ofDays(180);

  private RefundValidators() {}

  public static List<ValidationRule> defaultRules(RefundReasonRegistry reasonRegistry) {
    return defaultRules(reasonRegistry, DEFAULT_WINDOW);
  }

  public static List<ValidationRule> defaultRules(
      RefundReasonRegistry reasonRegistry, Duration window) {
    return List.of(
        new NegativeRefundAmountRule(),
        new RefundCurrencyMismatchRule(),
        new RefundExceedsBalanceRule(),
        new DuplicateRefundReferenceRule(),
        new InvalidRefundReasonRule(reasonRegistry),
        new ExpiredRefundWindowRule(window));
  }

  public static RefundValidator standard(RefundReasonRegistry reasonRegistry) {
    return new DefaultRefundValidator(defaultRules(reasonRegistry));
  }

  public static RefundValidator withRules(
      RefundReasonRegistry reasonRegistry, List<ValidationRule> additionalRules) {
    List<ValidationRule> combined = new ArrayList<>(defaultRules(reasonRegistry));
    combined.addAll(additionalRules);
    return new DefaultRefundValidator(combined);
  }
}
