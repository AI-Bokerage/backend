package io.genfin.ledger.internal.account;

import io.genfin.ledger.account.AccountType;
import io.genfin.ledger.account.AccountTypeDescriptor;
import io.genfin.ledger.port.account.AccountTypeRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultAccountTypeRegistry implements AccountTypeRegistry {

  private final ConcurrentMap<String, AccountTypeDescriptor> descriptors =
      new ConcurrentHashMap<>();

  @Override
  public void register(AccountTypeDescriptor descriptor) {
    descriptors.put(descriptor.type().code(), descriptor);
  }

  @Override
  public Optional<AccountTypeDescriptor> find(AccountType type) {
    return Optional.ofNullable(descriptors.get(type.code()));
  }

  @Override
  public List<AccountTypeDescriptor> findAll() {
    return List.copyOf(descriptors.values());
  }
}
