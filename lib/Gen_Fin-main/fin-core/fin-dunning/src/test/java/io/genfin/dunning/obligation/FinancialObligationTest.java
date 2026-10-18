package io.genfin.dunning.obligation;

import static io.genfin.dunning.support.TestCurrencies.USD;
import static io.genfin.dunning.support.TestObligationTypes.INVOICE;
import static io.genfin.dunning.support.TestObligationTypes.LOAN_INSTALLMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.dunning.reference.Reference;
import io.genfin.money.money.Money;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FinancialObligationTest {

  private static final Instant DUE_DATE = Instant.parse("2026-06-01T00:00:00Z");

  @Test
  void wrapsAnyKindOfReceivableThroughTheSameShape() {
    FinancialObligation invoice =
        FinancialObligation.of(
            Money.of("100.00", USD), DUE_DATE, INVOICE, Reference.obligationSource("inv-1"));
    FinancialObligation installment =
        FinancialObligation.of(
            Money.of("250.00", USD),
            DUE_DATE,
            LOAN_INSTALLMENT,
            Reference.obligationSource("loan-1-installment-3"));

    assertThat(invoice.type()).isEqualTo(INVOICE);
    assertThat(installment.type()).isEqualTo(LOAN_INSTALLMENT);
  }

  @Test
  void isOverdueOnlyStrictlyAfterTheDueDate() {
    FinancialObligation obligation =
        FinancialObligation.of(
            Money.of("100.00", USD), DUE_DATE, INVOICE, Reference.obligationSource("inv-1"));

    assertThat(obligation.isOverdueAsOf(DUE_DATE)).isFalse();
    assertThat(obligation.isOverdueAsOf(DUE_DATE.plusSeconds(1))).isTrue();
    assertThat(obligation.isOverdueAsOf(DUE_DATE.minusSeconds(1))).isFalse();
  }

  @Test
  void rejectsANegativeAmount() {
    assertThatThrownBy(
            () ->
                FinancialObligation.of(
                    Money.of("-1.00", USD), DUE_DATE, INVOICE, Reference.obligationSource("inv-1")))
        .isInstanceOf(RuntimeException.class);
  }
}
