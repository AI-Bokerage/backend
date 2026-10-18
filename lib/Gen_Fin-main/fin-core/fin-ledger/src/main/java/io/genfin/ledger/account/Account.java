package io.genfin.ledger.account;

import io.genfin.api.domain.Entity;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.ChartOfAccountsId;
import java.util.Optional;

/**
 * A single entry in a {@link ChartOfAccounts}. Gen-Fin hardcodes no account names or types - {@code
 * code}, {@code name}, {@link AccountType} and {@link AccountCategory} are all application-supplied
 * data, never framework constants.
 */
public final class Account extends Entity<AccountId> {

  private final ChartOfAccountsId chartOfAccountsId;
  private final String code;
  private final String name;
  private final AccountType type;
  private final AccountCategory category;
  private final AccountId parentId;
  private final AccountAttributes attributes;
  private final AccountMetadata metadata;
  private AccountStatus status;

  public Account(
      AccountId id,
      ChartOfAccountsId chartOfAccountsId,
      String code,
      String name,
      AccountType type,
      AccountCategory category,
      AccountId parentId,
      AccountAttributes attributes,
      AccountMetadata metadata) {
    super(id);
    this.chartOfAccountsId =
        Validate.notNull(chartOfAccountsId, "chartOfAccountsId must not be null.");
    this.code = Validate.notBlank(code, "code must not be blank.");
    this.name = Validate.notBlank(name, "name must not be blank.");
    this.type = Validate.notNull(type, "type must not be null.");
    this.category = category;
    this.parentId = parentId;
    this.attributes = Validate.notNull(attributes, "attributes must not be null.");
    this.metadata = Validate.notNull(metadata, "metadata must not be null.");
    this.status = AccountStatus.ACTIVE;
  }

  public static Account open(
      AccountId id,
      ChartOfAccountsId chartOfAccountsId,
      String code,
      String name,
      AccountType type) {
    return new Account(
        id,
        chartOfAccountsId,
        code,
        name,
        type,
        null,
        null,
        AccountAttributes.standard(),
        AccountMetadata.empty());
  }

  public ChartOfAccountsId chartOfAccountsId() {
    return chartOfAccountsId;
  }

  public String code() {
    return code;
  }

  public String name() {
    return name;
  }

  public AccountType type() {
    return type;
  }

  public AccountClassification classification() {
    return type.classification();
  }

  public Optional<AccountCategory> category() {
    return Optional.ofNullable(category);
  }

  public Optional<AccountId> parentId() {
    return Optional.ofNullable(parentId);
  }

  public AccountAttributes attributes() {
    return attributes;
  }

  public AccountMetadata metadata() {
    return metadata;
  }

  public AccountStatus status() {
    return status;
  }

  public boolean isPostingAllowed() {
    return status == AccountStatus.ACTIVE && attributes.postingAllowed();
  }

  public void deactivate() {
    Validate.state(status != AccountStatus.CLOSED, "Cannot deactivate a closed account.");
    status = AccountStatus.INACTIVE;
  }

  public void reactivate() {
    Validate.state(status != AccountStatus.CLOSED, "Cannot reactivate a closed account.");
    status = AccountStatus.ACTIVE;
  }

  public void close() {
    status = AccountStatus.CLOSED;
  }
}
