package io.genfin.ledger.port.account;

import io.genfin.ledger.account.AccountTypeDescriptor;
import java.util.List;

/**
 * Supplies the catalog of {@link AccountTypeDescriptor}s a deployment recognizes. Gen-Fin ships no
 * default implementation - unlike {@code RefundReasonProvider}'s standard catalog, an application's
 * account types (Cash, Revenue, Tax, ...) are entirely its own to define and register.
 */
@FunctionalInterface
public interface AccountTypeProvider {

  List<AccountTypeDescriptor> provide();
}
