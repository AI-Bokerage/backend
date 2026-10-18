package io.genfin.payment.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;

public record SettlementResult(Money grossAmount, Money fees, Money netAmount)
    implements ValueObject {}
