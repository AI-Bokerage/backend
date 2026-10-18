package io.genfin.ledger.account;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Describes an {@link AccountType} *kind* - its human-readable label - as opposed to the bare code
 * carried on an account itself. Mirrors {@code io.genfin.refund.reason.RefundReasonDescriptor}.
 */
public record AccountTypeDescriptor(AccountType type, String displayName) implements ValueObject {

  public AccountTypeDescriptor {
    Validate.notNull(type, "type must not be null.");
    Validate.notBlank(displayName, "displayName must not be blank.");
  }
}
