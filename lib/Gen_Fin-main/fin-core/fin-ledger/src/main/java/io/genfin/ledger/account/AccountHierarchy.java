package io.genfin.ledger.account;

import io.genfin.api.domain.ValueObject;
import io.genfin.ledger.id.AccountId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The parent/child relationships between {@link Account}s in a {@link ChartOfAccounts}, derived
 * from each account's own {@link Account#parentId()}. A pure, deterministic view over existing data
 * - not itself an extension point.
 */
public final class AccountHierarchy implements ValueObject {

  private final Map<AccountId, AccountId> parentOf;
  private final Map<AccountId, List<AccountId>> childrenOf;
  private final List<AccountId> roots;

  private AccountHierarchy(
      Map<AccountId, AccountId> parentOf,
      Map<AccountId, List<AccountId>> childrenOf,
      List<AccountId> roots) {
    this.parentOf = parentOf;
    this.childrenOf = childrenOf;
    this.roots = roots;
  }

  public static AccountHierarchy of(Collection<Account> accounts) {
    Map<AccountId, AccountId> parentOf = new LinkedHashMap<>();
    Map<AccountId, List<AccountId>> childrenOf = new LinkedHashMap<>();
    List<AccountId> roots = new ArrayList<>();
    for (Account account : accounts) {
      childrenOf.putIfAbsent(account.id(), new ArrayList<>());
      Optional<AccountId> parentId = account.parentId();
      if (parentId.isPresent()) {
        parentOf.put(account.id(), parentId.get());
        childrenOf.computeIfAbsent(parentId.get(), key -> new ArrayList<>()).add(account.id());
      } else {
        roots.add(account.id());
      }
    }
    return new AccountHierarchy(parentOf, childrenOf, roots);
  }

  public Optional<AccountId> parentOf(AccountId accountId) {
    return Optional.ofNullable(parentOf.get(accountId));
  }

  public List<AccountId> childrenOf(AccountId accountId) {
    return List.copyOf(childrenOf.getOrDefault(accountId, List.of()));
  }

  public List<AccountId> roots() {
    return List.copyOf(roots);
  }

  /** Ancestors of {@code accountId}, nearest parent first. */
  public List<AccountId> ancestorsOf(AccountId accountId) {
    List<AccountId> ancestors = new ArrayList<>();
    Optional<AccountId> current = parentOf(accountId);
    while (current.isPresent()) {
      ancestors.add(current.get());
      current = parentOf(current.get());
    }
    return List.copyOf(ancestors);
  }

  public boolean isDescendantOf(AccountId candidate, AccountId ancestor) {
    return ancestorsOf(candidate).contains(ancestor);
  }
}
