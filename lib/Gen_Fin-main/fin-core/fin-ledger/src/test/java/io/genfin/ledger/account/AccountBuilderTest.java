package io.genfin.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.ChartOfAccountsId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountBuilderTest {

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

  private enum TestAccountCategory implements AccountCategory {
    CURRENT;

    @Override
    public String code() {
      return name();
    }
  }

  @Test
  void buildsAnAccountWithDefaultsWhenOptionalCollaboratorsAreOmitted() {
    ChartOfAccountsId chartOfAccountsId = ChartOfAccountsId.generate();

    Account account =
        AccountBuilder.newAccount()
            .chartOfAccountsId(chartOfAccountsId)
            .code("1000")
            .name("Cash")
            .type(TestAccountType.ASSET_TYPE)
            .build();

    assertThat(account.id()).isNotNull();
    assertThat(account.chartOfAccountsId()).isEqualTo(chartOfAccountsId);
    assertThat(account.code()).isEqualTo("1000");
    assertThat(account.name()).isEqualTo("Cash");
    assertThat(account.type()).isEqualTo(TestAccountType.ASSET_TYPE);
    assertThat(account.category()).isEmpty();
    assertThat(account.parentId()).isEmpty();
    assertThat(account.attributes()).isEqualTo(AccountAttributes.standard());
    assertThat(account.metadata()).isEqualTo(AccountMetadata.empty());
  }

  @Test
  void buildsAnAccountWithEveryCollaboratorSupplied() {
    ChartOfAccountsId chartOfAccountsId = ChartOfAccountsId.generate();
    AccountId id = AccountId.generate();
    AccountId parentId = AccountId.generate();
    AccountAttributes attributes = AccountAttributes.systemControl();
    AccountMetadata metadata = new AccountMetadata(Map.of("k", "v"));

    Account account =
        AccountBuilder.newAccount()
            .id(id)
            .chartOfAccountsId(chartOfAccountsId)
            .code("2000")
            .name("Accounts Payable")
            .type(TestAccountType.ASSET_TYPE)
            .category(TestAccountCategory.CURRENT)
            .parentId(parentId)
            .attributes(attributes)
            .metadata(metadata)
            .build();

    assertThat(account.id()).isEqualTo(id);
    assertThat(account.parentId()).contains(parentId);
    assertThat(account.category()).contains(TestAccountCategory.CURRENT);
    assertThat(account.attributes()).isEqualTo(attributes);
    assertThat(account.metadata()).isEqualTo(metadata);
  }
}
