package io.genfin.ledger.account;

/**
 * The five fundamental double-entry categories. Unlike {@link AccountType}, this is a closed,
 * structural taxonomy - not an application-specific business concept - because normal-balance
 * direction (debit vs. credit) is intrinsic to double-entry bookkeeping itself, the same way {@code
 * DEBIT}/{@code CREDIT} would be.
 */
public enum AccountClassification {
  ASSET,
  LIABILITY,
  EQUITY,
  REVENUE,
  EXPENSE;

  /** True for classifications whose balance increases on the debit side (Asset, Expense). */
  public boolean isDebitNormal() {
    return this == ASSET || this == EXPENSE;
  }
}
