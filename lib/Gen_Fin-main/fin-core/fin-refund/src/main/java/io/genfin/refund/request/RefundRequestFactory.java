package io.genfin.refund.request;

import io.genfin.api.exception.GenFinException;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.money.Money;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.port.validation.RefundRequestValidator;
import io.genfin.refund.reason.ReasonFactory;
import io.genfin.refund.reason.RefundReason;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.validation.RefundRequestValidators;
import java.util.List;

/**
 * Creates {@link RefundRequest}s validated against the {@link RefundRequestValidator} registered in
 * an {@link ExtensionRegistry}, falling back to {@link RefundRequestValidators#standard} scoped to
 * the {@link ReasonFactory}-resolved reason registry.
 */
public final class RefundRequestFactory {

  private RefundRequestFactory() {}

  public static RefundRequest create(
      ExtensionRegistry registry, Money amount, RefundReason reason, Reference paymentReference) {
    RefundRequest request =
        RefundRequestBuilder.newRequest()
            .amount(amount)
            .reason(reason)
            .paymentReference(paymentReference)
            .build();
    List<PolicyViolation> violations =
        registry
            .find(RefundRequestValidator.class)
            .orElseGet(() -> RefundRequestValidators.standard(ReasonFactory.from(registry)))
            .validate(request);
    if (!violations.isEmpty()) {
      throw new GenFinException(
          RefundErrorCode.VALIDATION_FAILED, "Refund request failed validation: " + violations);
    }
    return request;
  }
}
