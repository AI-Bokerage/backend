package io.genfin.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.ledger.port.account.AccountTypeRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountTypeRegistryTest {

  private enum TestAccountType implements AccountType {
    CASH(AccountClassification.ASSET),
    SALES(AccountClassification.REVENUE);

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
  void startsEmptyUntilAnApplicationRegistersItsOwnTypes() {
    AccountTypeRegistry registry = AccountTypeRegistries.empty();

    assertThat(registry.findAll()).isEmpty();

    registry.register(new AccountTypeDescriptor(TestAccountType.CASH, "Cash"));

    assertThat(registry.find(TestAccountType.CASH)).isPresent();
    assertThat(registry.require(TestAccountType.CASH).displayName()).isEqualTo("Cash");
  }

  @Test
  void requireThrowsForAnUnregisteredType() {
    AccountTypeRegistry registry = AccountTypeRegistries.empty();

    assertThatThrownBy(() -> registry.require(TestAccountType.SALES))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void withProviderRegistersEveryDescriptorUpFront() {
    AccountTypeRegistry registry =
        AccountTypeRegistries.withProvider(
            () ->
                List.of(
                    new AccountTypeDescriptor(TestAccountType.CASH, "Cash"),
                    new AccountTypeDescriptor(TestAccountType.SALES, "Sales")));

    assertThat(registry.findAll()).hasSize(2);
  }
}
