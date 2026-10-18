package io.genfin.ledger.account;

import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.ChartOfAccountsId;

/**
 * Builds {@link Account}s. Preferred over the canonical constructor for readability at call sites
 * given how many optional collaborators an account carries (category, parent, attributes,
 * metadata). Mirrors {@code io.genfin.reconciliation.reconciliation.ReconciliationBuilder}.
 */
public final class AccountBuilder {

  private AccountId id;
  private ChartOfAccountsId chartOfAccountsId;
  private String code;
  private String name;
  private AccountType type;
  private AccountCategory category;
  private AccountId parentId;
  private AccountAttributes attributes = AccountAttributes.standard();
  private AccountMetadata metadata = AccountMetadata.empty();

  private AccountBuilder() {}

  public static AccountBuilder newAccount() {
    return new AccountBuilder();
  }

  public AccountBuilder id(AccountId id) {
    this.id = id;
    return this;
  }

  public AccountBuilder chartOfAccountsId(ChartOfAccountsId chartOfAccountsId) {
    this.chartOfAccountsId = chartOfAccountsId;
    return this;
  }

  public AccountBuilder code(String code) {
    this.code = code;
    return this;
  }

  public AccountBuilder name(String name) {
    this.name = name;
    return this;
  }

  public AccountBuilder type(AccountType type) {
    this.type = type;
    return this;
  }

  public AccountBuilder category(AccountCategory category) {
    this.category = category;
    return this;
  }

  public AccountBuilder parentId(AccountId parentId) {
    this.parentId = parentId;
    return this;
  }

  public AccountBuilder attributes(AccountAttributes attributes) {
    this.attributes = attributes;
    return this;
  }

  public AccountBuilder metadata(AccountMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public Account build() {
    return new Account(
        id == null ? AccountId.generate() : id,
        chartOfAccountsId,
        code,
        name,
        type,
        category,
        parentId,
        attributes,
        metadata);
  }
}
