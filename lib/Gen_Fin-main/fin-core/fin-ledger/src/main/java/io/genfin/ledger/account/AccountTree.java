package io.genfin.ledger.account;

import io.genfin.api.domain.ValueObject;
import io.genfin.ledger.id.AccountId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The materialized parent/child tree of a {@link ChartOfAccounts}, built from an {@link
 * AccountHierarchy} - used for rendering (e.g. a financial-statement outline) where {@link
 * AccountHierarchy}'s flat lookups are less convenient.
 */
public record AccountTree(List<Node> roots) implements ValueObject {

  public AccountTree {
    roots = List.copyOf(roots);
  }

  public static AccountTree of(Collection<Account> accounts) {
    Map<AccountId, Account> byId =
        accounts.stream().collect(Collectors.toMap(Account::id, account -> account));
    AccountHierarchy hierarchy = AccountHierarchy.of(accounts);
    List<Node> roots =
        hierarchy.roots().stream().map(id -> buildNode(id, byId, hierarchy)).toList();
    return new AccountTree(roots);
  }

  private static Node buildNode(
      AccountId id, Map<AccountId, Account> byId, AccountHierarchy hierarchy) {
    List<Node> children =
        hierarchy.childrenOf(id).stream()
            .map(childId -> buildNode(childId, byId, hierarchy))
            .toList();
    return new Node(byId.get(id), children);
  }

  /** All accounts in the tree, parents before children. */
  public List<Account> flatten() {
    List<Account> flattened = new ArrayList<>();
    roots.forEach(node -> flatten(node, flattened));
    return List.copyOf(flattened);
  }

  private static void flatten(Node node, List<Account> into) {
    into.add(node.account());
    node.children().forEach(child -> flatten(child, into));
  }

  public Optional<Node> find(AccountId accountId) {
    for (Node root : roots) {
      Optional<Node> found = find(root, accountId);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static Optional<Node> find(Node node, AccountId accountId) {
    if (node.account().id().equals(accountId)) {
      return Optional.of(node);
    }
    for (Node child : node.children()) {
      Optional<Node> found = find(child, accountId);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** One node of the tree: an account and its already-built children. */
  public record Node(Account account, List<Node> children) implements ValueObject {

    public Node {
      children = List.copyOf(children);
    }
  }
}
