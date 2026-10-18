package io.genfin.ledger.account;

import io.genfin.api.domain.Entity;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.ChartOfAccountsId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of {@link Account}s a {@code Ledger} posts against, keyed by both {@link AccountId} and
 * application-assigned account code. Owned by a {@code Ledger} via {@link ChartOfAccountsId} only -
 * mirrors how the ledger aggregate holds every other concept by reference.
 */
public final class ChartOfAccounts extends Entity<ChartOfAccountsId> {

  private final Map<AccountId, Account> accountsById = new LinkedHashMap<>();
  private final Map<String, AccountId> idsByCode = new LinkedHashMap<>();

  public ChartOfAccounts(ChartOfAccountsId id) {
    super(id);
  }

  public static ChartOfAccounts open() {
    return new ChartOfAccounts(ChartOfAccountsId.generate());
  }

  /** Adds {@code account} to this chart. The account's code must be unique within the chart. */
  public void add(Account account) {
    Validate.notNull(account, "account must not be null.");
    Validate.state(
        account.chartOfAccountsId().equals(id()),
        "Account belongs to a different chart of accounts.");
    Validate.state(
        !idsByCode.containsKey(account.code()),
        "An account with code '" + account.code() + "' is already registered.");
    accountsById.put(account.id(), account);
    idsByCode.put(account.code(), account.id());
  }

  public Optional<Account> find(AccountId accountId) {
    return Optional.ofNullable(accountsById.get(accountId));
  }

  public Optional<Account> findByCode(String code) {
    return Optional.ofNullable(idsByCode.get(code)).flatMap(this::find);
  }

  public List<Account> accounts() {
    return List.copyOf(accountsById.values());
  }

  public AccountHierarchy hierarchy() {
    return AccountHierarchy.of(accounts());
  }

  public AccountTree tree() {
    return AccountTree.of(accounts());
  }
}
