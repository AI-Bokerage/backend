package io.genfin.ledger.account;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A named, application-defined rollup of accounts for reporting (e.g. a financial-statement line
 * item), independent of the parent/child {@link AccountHierarchy} - membership can cut across the
 * hierarchy.
 */
public record AccountGroup(String code, String displayName, Set<AccountId> memberIds)
    implements ValueObject {

  public AccountGroup {
    Validate.notBlank(code, "code must not be blank.");
    Validate.notBlank(displayName, "displayName must not be blank.");
    memberIds = Set.copyOf(memberIds);
  }

  public static AccountGroup of(String code, String displayName) {
    return new AccountGroup(code, displayName, Set.of());
  }

  public AccountGroup withMember(AccountId accountId) {
    Validate.notNull(accountId, "accountId must not be null.");
    Set<AccountId> updated = new LinkedHashSet<>(memberIds);
    updated.add(accountId);
    return new AccountGroup(code, displayName, updated);
  }

  public boolean contains(AccountId accountId) {
    return memberIds.contains(accountId);
  }
}
