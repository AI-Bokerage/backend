package io.genfin.payment.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceModelTest {

  @Test
  void invoiceAndCustomerConvenienceFactories() {
    Reference invoiceRef = Reference.invoice("INV-1");
    Reference customerRef = Reference.customer("CUST-1");

    assertThat(invoiceRef.type()).isEqualTo(StandardReferenceType.INVOICE);
    assertThat(customerRef.type()).isEqualTo(StandardReferenceType.CUSTOMER);
  }

  @Test
  void blankValueIsRejected() {
    assertThatThrownBy(() -> Reference.of(StandardReferenceType.ORDER, " "))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void collectionFiltersByType() {
    ReferenceCollection collection =
        ReferenceCollection.of(
            List.of(
                Reference.invoice("INV-1"),
                Reference.customer("CUST-1"),
                Reference.invoice("INV-2")));

    assertThat(collection.byType(StandardReferenceType.INVOICE)).hasSize(2);
    assertThat(collection.byType(StandardReferenceType.CUSTOMER)).hasSize(1);
  }
}
