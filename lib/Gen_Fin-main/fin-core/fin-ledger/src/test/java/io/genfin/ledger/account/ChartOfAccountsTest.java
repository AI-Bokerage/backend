package io.genfin.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.ChartOfAccountsId;
import org.junit.jupiter.api.Test;

class ChartOfAccountsTest {

  private enum TestAccountType implements AccountType {
    ASSET_TYPE(AccountClassification.ASSET);

    private final AccountClassification classification;

    TestAccountType(AccountClassification classification) {
      this.classification = classification;
    }

    @Override
    public String code() {
      return name();
    }

    @Override
    public AccountClassification classification() {
      return classification;
    }
  }

  @Test
  void addRejectsADuplicateCodeAndAnAccountFromAnotherChart() {
    ChartOfAccounts chart = ChartOfAccounts.open();
    Account parent =
        Account.open(
            AccountId.generate(), chart.id(), "1000", "Parent", TestAccountType.ASSET_TYPE);
    chart.add(parent);

    Account duplicateCode =
        Account.open(AccountId.generate(), chart.id(), "1000", "Other", TestAccountType.ASSET_TYPE);
    assertThatThrownBy(() -> chart.add(duplicateCode)).isInstanceOf(IllegalStateException.class);

    Account wrongChart =
        Account.open(
            AccountId.generate(),
            ChartOfAccountsId.generate(),
            "2000",
            "Wrong Chart",
            TestAccountType.ASSET_TYPE);
    assertThatThrownBy(() -> chart.add(wrongChart)).isInstanceOf(IllegalStateException.class);

    assertThat(chart.find(parent.id())).contains(parent);
    assertThat(chart.findByCode("1000")).contains(parent);
  }

  @Test
  void hierarchyAndTreeReflectParentChildLinks() {
    ChartOfAccounts chart = ChartOfAccounts.open();
    Account parent =
        Account.open(
            AccountId.generate(), chart.id(), "1000", "Assets", TestAccountType.ASSET_TYPE);
    chart.add(parent);
    Account child =
        new Account(
            AccountId.generate(),
            chart.id(),
            "1100",
            "Cash",
            TestAccountType.ASSET_TYPE,
            null,
            parent.id(),
            AccountAttributes.standard(),
            AccountMetadata.empty());
    chart.add(child);

    AccountHierarchy hierarchy = chart.hierarchy();
    assertThat(hierarchy.roots()).containsExactly(parent.id());
    assertThat(hierarchy.childrenOf(parent.id())).containsExactly(child.id());
    assertThat(hierarchy.parentOf(child.id())).contains(parent.id());
    assertThat(hierarchy.ancestorsOf(child.id())).containsExactly(parent.id());
    assertThat(hierarchy.isDescendantOf(child.id(), parent.id())).isTrue();

    AccountTree tree = chart.tree();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.flatten()).containsExactly(parent, child);
    assertThat(tree.find(child.id())).isPresent();
  }

  @Test
  void accountStatusTransitionsAreGuardedAndClosedIsTerminal() {
    Account account =
        Account.open(
            AccountId.generate(),
            ChartOfAccountsId.generate(),
            "1000",
            "Cash",
            TestAccountType.ASSET_TYPE);

    assertThat(account.isPostingAllowed()).isTrue();

    account.deactivate();
    assertThat(account.status()).isEqualTo(AccountStatus.INACTIVE);
    assertThat(account.isPostingAllowed()).isFalse();

    account.reactivate();
    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);

    account.close();
    assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
    assertThatThrownBy(account::reactivate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void accountGroupIsAnImmutableRollupIndependentOfHierarchy() {
    AccountGroup group = AccountGroup.of("CURRENT_ASSETS", "Current Assets");
    AccountId memberId = AccountId.generate();

    AccountGroup withMember = group.withMember(memberId);

    assertThat(group.contains(memberId)).isFalse();
    assertThat(withMember.contains(memberId)).isTrue();
  }
}
