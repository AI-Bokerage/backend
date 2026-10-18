package io.genfin.ledger.port.account;

import io.genfin.api.exception.ValidationException;
import io.genfin.ledger.account.AccountType;
import io.genfin.ledger.account.AccountTypeDescriptor;
import java.util.List;
import java.util.Optional;

/**
 * Registry of the {@link AccountType}s an application has registered for its Chart of Accounts.
 * Mirrors {@code io.genfin.refund.port.reason.RefundReasonRegistry} / {@code
 * io.genfin.reconciliation.port.discrepancy.DiscrepancyReasonRegistry}.
 */
public interface AccountTypeRegistry {

  void register(AccountTypeDescriptor descriptor);

  Optional<AccountTypeDescriptor> find(AccountType type);

  default AccountTypeDescriptor require(AccountType type) {
    return find(type)
        .orElseThrow(() -> new ValidationException("Unregistered account type: " + type.code()));
  }

  List<AccountTypeDescriptor> findAll();
}
