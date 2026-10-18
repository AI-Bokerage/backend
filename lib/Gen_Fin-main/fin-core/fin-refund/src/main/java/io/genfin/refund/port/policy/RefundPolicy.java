package io.genfin.refund.port.policy;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.policy.RefundPolicyContext;
import io.genfin.refund.policy.RefundPolicyDecision;

/**
 * The single entry point for deciding whether a requested refund is permitted: composes {@link
 * RefundWindowPolicy}, {@link MaximumRefundPolicy}, {@link ReasonPolicy} and {@link ApprovalPolicy}
 * so callers evaluate one policy instead of wiring each sub-policy themselves.
 */
public interface RefundPolicy extends Extension {

  RefundPolicyDecision evaluate(RefundPolicyContext context);
}
