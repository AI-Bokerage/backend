package io.genfin.ledger.account;

/** The lifecycle status of an {@link Account} within a {@link ChartOfAccounts}. */
public enum AccountStatus {

  /** Open and eligible to receive postings (subject to {@link AccountAttributes}). */
  ACTIVE,

  /** Temporarily suspended from new postings; can be reactivated. */
  INACTIVE,

  /** Permanently closed. Terminal - an account can never leave this status. */
  CLOSED
}
