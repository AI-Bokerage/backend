package io.genfin.ledger.fact;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * The kind of upstream event a {@link FinancialFact} originated from (e.g. "INVOICE_PAID",
 * "REFUND_COMPLETED"). Deliberately an opaque code, not an enum — consuming applications register
 * whatever fact types their posting rules need, this module hardcodes none of them.
 */
public record FinancialFactType(String code) implements ValueObject {

  public FinancialFactType {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static FinancialFactType of(String code) {
    return new FinancialFactType(code);
  }
}
