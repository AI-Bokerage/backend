package io.genfin.reconciliation.rule;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.port.tolerance.ToleranceCalculator;
import io.genfin.reconciliation.tolerance.Tolerance;
import java.time.Instant;

/**
 * Everything a {@code ReconciliationRule} may need beyond the two {@code ComparisonRecord}s being
 * compared. Every field but {@code asOf} is optional — a {@code null} value means the corresponding
 * rule(s) have nothing to check and report no {@link RuleResult}, mirroring fin-refund's {@code
 * ValidationContext}. The boolean-shaped fields are {@link Boolean} rather than {@code boolean} for
 * exactly this reason: {@code null} means "not evaluated by the caller", {@code false} means
 * "evaluated and absent/duplicated/expired".
 */
public record RuleContext(
    Instant asOf,
    Tolerance amountTolerance,
    ToleranceCalculator toleranceCalculator,
    Boolean refundExists,
    Boolean duplicatePayment,
    Boolean duplicateRefund,
    Boolean settlementExists,
    Boolean paymentExists,
    Boolean expired)
    implements ValueObject {

  public RuleContext {
    Validate.notNull(asOf, "asOf must not be null.");
  }

  public static RuleContext at(Instant asOf) {
    return new RuleContext(asOf, null, null, null, null, null, null, null, null);
  }

  public RuleContext withAmountTolerance(
      Tolerance amountTolerance, ToleranceCalculator calculator) {
    return new RuleContext(
        asOf,
        amountTolerance,
        calculator,
        refundExists,
        duplicatePayment,
        duplicateRefund,
        settlementExists,
        paymentExists,
        expired);
  }

  public RuleContext withRefundExists(boolean refundExists) {
    return new RuleContext(
        asOf,
        amountTolerance,
        toleranceCalculator,
        refundExists,
        duplicatePayment,
        duplicateRefund,
        settlementExists,
        paymentExists,
        expired);
  }

  public RuleContext withDuplicatePayment(boolean duplicatePayment) {
    return new RuleContext(
        asOf,
        amountTolerance,
        toleranceCalculator,
        refundExists,
        duplicatePayment,
        duplicateRefund,
        settlementExists,
        paymentExists,
        expired);
  }

  public RuleContext withDuplicateRefund(boolean duplicateRefund) {
    return new RuleContext(
        asOf,
        amountTolerance,
        toleranceCalculator,
        refundExists,
        duplicatePayment,
        duplicateRefund,
        settlementExists,
        paymentExists,
        expired);
  }

  public RuleContext withSettlementExists(boolean settlementExists) {
    return new RuleContext(
        asOf,
        amountTolerance,
        toleranceCalculator,
        refundExists,
        duplicatePayment,
        duplicateRefund,
        settlementExists,
        paymentExists,
        expired);
  }

  public RuleContext withPaymentExists(boolean paymentExists) {
    return new RuleContext(
        asOf,
        amountTolerance,
        toleranceCalculator,
        refundExists,
        duplicatePayment,
        duplicateRefund,
        settlementExists,
        paymentExists,
        expired);
  }

  public RuleContext withExpired(boolean expired) {
    return new RuleContext(
        asOf,
        amountTolerance,
        toleranceCalculator,
        refundExists,
        duplicatePayment,
        duplicateRefund,
        settlementExists,
        paymentExists,
        expired);
  }
}
