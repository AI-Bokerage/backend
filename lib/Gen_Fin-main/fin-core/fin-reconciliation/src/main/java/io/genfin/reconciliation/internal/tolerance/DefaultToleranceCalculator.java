package io.genfin.reconciliation.internal.tolerance;

import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.tolerance.CustomToleranceRuleRegistry;
import io.genfin.reconciliation.port.tolerance.ToleranceCalculator;
import io.genfin.reconciliation.tolerance.AmountTolerance;
import io.genfin.reconciliation.tolerance.CurrencyTolerance;
import io.genfin.reconciliation.tolerance.CustomTolerance;
import io.genfin.reconciliation.tolerance.DateTolerance;
import io.genfin.reconciliation.tolerance.PercentageTolerance;
import io.genfin.reconciliation.tolerance.Tolerance;
import io.genfin.reconciliation.tolerance.ToleranceResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * The standard {@link ToleranceCalculator}: interprets each {@link Tolerance} kind structurally,
 * except {@link CustomTolerance} which it delegates entirely to the rule registered under that
 * tolerance's name.
 */
public final class DefaultToleranceCalculator implements ToleranceCalculator {

  private final CustomToleranceRuleRegistry customRules;

  public DefaultToleranceCalculator(CustomToleranceRuleRegistry customRules) {
    this.customRules = Validate.notNull(customRules, "customRules must not be null.");
  }

  @Override
  public ToleranceResult evaluate(
      Tolerance tolerance, ComparisonRecord left, ComparisonRecord right) {
    Validate.notNull(tolerance, "tolerance must not be null.");
    Validate.notNull(left, "left must not be null.");
    Validate.notNull(right, "right must not be null.");
    return switch (tolerance) {
      case AmountTolerance amountTolerance -> evaluateAmount(amountTolerance, left, right);
      case DateTolerance dateTolerance -> evaluateDate(dateTolerance, left, right);
      case PercentageTolerance percentageTolerance ->
          evaluatePercentage(percentageTolerance, left, right);
      case CurrencyTolerance currencyTolerance -> evaluateCurrency(currencyTolerance, left, right);
      case CustomTolerance customTolerance -> evaluateCustom(customTolerance, left, right);
    };
  }

  private ToleranceResult evaluateAmount(
      AmountTolerance tolerance, ComparisonRecord left, ComparisonRecord right) {
    if (left.amount() == null || right.amount() == null) {
      return ToleranceResult.notApplicable("An amount is missing on one side.");
    }
    if (!left.amount().currency().equals(right.amount().currency())) {
      return ToleranceResult.notApplicable("Currencies differ; amount tolerance does not apply.");
    }
    Money gap = left.amount().subtract(right.amount()).abs();
    if (gap.compareTo(tolerance.maxVariance()) > 0) {
      return ToleranceResult.exceeds(
          "Amounts differ by " + gap + ", beyond tolerance " + tolerance.maxVariance() + ".");
    }
    double confidence = confidenceFor(gap.amount(), tolerance.maxVariance().amount());
    return ToleranceResult.within(confidence, "Amounts differ by " + gap + ", within tolerance.");
  }

  private ToleranceResult evaluateDate(
      DateTolerance tolerance, ComparisonRecord left, ComparisonRecord right) {
    if (left.timestamp() == null || right.timestamp() == null) {
      return ToleranceResult.notApplicable("A timestamp is missing on one side.");
    }
    Duration gap = Duration.between(left.timestamp(), right.timestamp()).abs();
    if (gap.compareTo(tolerance.maxVariance()) > 0) {
      return ToleranceResult.exceeds(
          "Timestamps differ by " + gap + ", beyond tolerance " + tolerance.maxVariance() + ".");
    }
    double confidence =
        confidenceFor(
            BigDecimal.valueOf(gap.toNanos()),
            BigDecimal.valueOf(tolerance.maxVariance().toNanos()));
    return ToleranceResult.within(
        confidence, "Timestamps differ by " + gap + ", within tolerance.");
  }

  private ToleranceResult evaluatePercentage(
      PercentageTolerance tolerance, ComparisonRecord left, ComparisonRecord right) {
    if (left.amount() == null || right.amount() == null) {
      return ToleranceResult.notApplicable("An amount is missing on one side.");
    }
    if (!left.amount().currency().equals(right.amount().currency())) {
      return ToleranceResult.notApplicable(
          "Currencies differ; percentage tolerance does not apply.");
    }
    if (left.amount().isZero()) {
      return ToleranceResult.notApplicable(
          "The reference amount is zero; percentage tolerance does not apply.");
    }
    Money gap = left.amount().subtract(right.amount()).abs();
    Money threshold = left.amount().abs().multiply(tolerance.maxVarianceFraction().abs());
    if (gap.compareTo(threshold) > 0) {
      return ToleranceResult.exceeds(
          "Amounts differ by "
              + gap
              + ", beyond the "
              + tolerance.maxVarianceFraction()
              + " tolerance ("
              + threshold
              + ").");
    }
    double confidence = confidenceFor(gap.amount(), threshold.amount());
    return ToleranceResult.within(
        confidence, "Amounts differ by " + gap + ", within the percentage tolerance.");
  }

  private ToleranceResult evaluateCurrency(
      CurrencyTolerance tolerance, ComparisonRecord left, ComparisonRecord right) {
    if (left.amount() == null || right.amount() == null) {
      return ToleranceResult.notApplicable("An amount is missing on one side.");
    }
    Currency leftCurrency = left.amount().currency();
    Currency rightCurrency = right.amount().currency();
    if (leftCurrency.equals(rightCurrency)) {
      return ToleranceResult.within(1.0, "Currencies match exactly.");
    }
    if (tolerance.equivalentCurrencies().contains(leftCurrency)
        && tolerance.equivalentCurrencies().contains(rightCurrency)) {
      return ToleranceResult.within(
          1.0,
          "Currencies "
              + leftCurrency.code()
              + " and "
              + rightCurrency.code()
              + " are configured as equivalent.");
    }
    return ToleranceResult.exceeds(
        "Currencies "
            + leftCurrency.code()
            + " and "
            + rightCurrency.code()
            + " are not equivalent.");
  }

  private ToleranceResult evaluateCustom(
      CustomTolerance tolerance, ComparisonRecord left, ComparisonRecord right) {
    return customRules.require(tolerance.ruleName()).evaluate(left, right);
  }

  /** Confidence decays linearly from 1.0 at zero variance to 0.0 at the tolerance boundary. */
  private double confidenceFor(BigDecimal gap, BigDecimal max) {
    if (max.signum() == 0) {
      return 0.0;
    }
    BigDecimal ratio = gap.divide(max, 6, RoundingMode.HALF_UP);
    double confidence = 1.0 - ratio.doubleValue();
    return Math.max(0.0, Math.min(1.0, confidence));
  }
}
