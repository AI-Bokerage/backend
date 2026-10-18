package io.genfin.payment.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;
import io.genfin.payment.id.PaymentId;

/**
 * How much of a total-due amount a specific payment contributed — for reconciling multiple payments
 * against one balance.
 */
public record PaymentAllocation(PaymentId paymentId, Money allocatedAmount)
    implements ValueObject {}
