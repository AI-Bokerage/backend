package io.genfin.refund.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;
import io.genfin.refund.id.RefundId;

/**
 * How much of a refundable balance a specific refund was allocated — for reconciling multiple
 * refunds requested against one payment.
 */
public record RefundAllocation(RefundId refundId, Money allocatedAmount) implements ValueObject {}
