package io.genfin.refund.internal.validation;

import io.genfin.api.validation.Validate;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import io.genfin.refund.port.validation.RefundRequestValidator;
import io.genfin.refund.reference.StandardReferenceType;
import io.genfin.refund.request.RefundRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Requires a PAYMENT reference and a reason permitted by the given {@link RefundReasonRegistry}.
 */
public final class DefaultRefundRequestValidator implements RefundRequestValidator {

  private final RefundReasonRegistry reasonRegistry;

  public DefaultRefundRequestValidator(RefundReasonRegistry reasonRegistry) {
    this.reasonRegistry = Validate.notNull(reasonRegistry, "reasonRegistry must not be null.");
  }

  @Override
  public List<PolicyViolation> validate(RefundRequest request) {
    Validate.notNull(request, "request must not be null.");
    List<PolicyViolation> violations = new ArrayList<>();
    if (request.references().byType(StandardReferenceType.PAYMENT).isEmpty()) {
      violations.add(
          new PolicyViolation(
              RefundErrorCode.VALIDATION_FAILED, "Refund request must reference a PAYMENT."));
    }
    if (reasonRegistry.find(request.reason()).isEmpty()) {
      violations.add(
          new PolicyViolation(
              RefundErrorCode.INVALID_REFUND_REASON,
              "Refund reason " + request.reason().code() + " is not permitted."));
    }
    return violations;
  }
}
