package io.genfin.ledger.account;

import io.genfin.ledger.internal.account.DefaultAccountTypeRegistry;
import io.genfin.ledger.port.account.AccountTypeProvider;
import io.genfin.ledger.port.account.AccountTypeRegistry;

/**
 * Factory for {@link AccountTypeRegistry} instances. Deliberately has no {@code standardCatalog()}
 * counterpart to {@code RefundReasonRegistries} - Gen-Fin defines no built-in account types, so
 * every registry starts empty until an application supplies its own {@link AccountTypeProvider}.
 */
public final class AccountTypeRegistries {

  private AccountTypeRegistries() {}

  public static AccountTypeRegistry empty() {
    return new DefaultAccountTypeRegistry();
  }

  public static AccountTypeRegistry withProvider(AccountTypeProvider provider) {
    AccountTypeRegistry registry = new DefaultAccountTypeRegistry();
    provider.provide().forEach(registry::register);
    return registry;
  }
}
