package io.genfin.refund.request;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.GenFinException;
import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.money.Money;
import io.genfin.refund.reason.StandardRefundReason;
import io.genfin.refund.reference.Reference;
import org.junit.jupiter.api.Test;

class RefundRequestBuilderAndFactoryTest {

  @Test
  void builderDefaultsIdAndRequestedAt() {
    RefundRequest request =
        RefundRequestBuilder.newRequest()
            .amount(Money.of("10.00", USD))
            .reason(StandardRefundReason.CUSTOMER_REQUEST)
            .paymentReference(Reference.payment("payment-1"))
            .build();

    assertThat(request.status()).isEqualTo(RefundRequestStatus.PENDING);
    assertThat(request.requestedAt()).isNotNull();
  }

  @Test
  void factoryAcceptsAKnownReasonUsingTheDefaultCatalog() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    RefundRequest request =
        RefundRequestFactory.create(
            registry,
            Money.of("10.00", USD),
            StandardRefundReason.CUSTOMER_REQUEST,
            Reference.payment("payment-1"));

    assertThat(request.status()).isEqualTo(RefundRequestStatus.PENDING);
  }

  @Test
  void factoryRejectsAnUnknownReasonWhenRegistryOnlyHasCatalogReasons() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    assertThatThrownBy(
            () ->
                RefundRequestFactory.create(
                    registry,
                    Money.of("10.00", USD),
                    () -> "NOT_A_STANDARD_REASON",
                    Reference.payment("payment-1")))
        .isInstanceOf(GenFinException.class);
  }
}
