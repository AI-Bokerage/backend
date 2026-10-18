package io.genfin.ledger.account;

import io.genfin.api.domain.ValueObject;

/**
 * Structural posting flags for an {@link Account}. These govern *how* the account participates in
 * posting, not what it is named - naming and classification belong to {@link AccountType} / {@link
 * AccountCategory}.
 */
public record AccountAttributes(
    boolean postingAllowed, boolean systemAccount, boolean requiresReconciliation)
    implements ValueObject {

  /** A normal, directly postable account. */
  public static AccountAttributes standard() {
    return new AccountAttributes(true, false, false);
  }

  /** A system/control account applications may use for rollups - never posted to directly. */
  public static AccountAttributes systemControl() {
    return new AccountAttributes(false, true, false);
  }
}
