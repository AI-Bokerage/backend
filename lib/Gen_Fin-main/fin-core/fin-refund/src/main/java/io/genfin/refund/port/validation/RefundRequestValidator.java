package io.genfin.refund.port.validation;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.policy.PolicyViolation;
import io.genfin.refund.request.RefundRequest;
import java.util.List;

/**
 * Validates a {@link RefundRequest} on its own terms, independent of any {@code Refund} policy
 * (window/maximum/approval) that applies once a refund is actually executed.
 */
public interface RefundRequestValidator extends Extension {

  List<PolicyViolation> validate(RefundRequest request);
}
