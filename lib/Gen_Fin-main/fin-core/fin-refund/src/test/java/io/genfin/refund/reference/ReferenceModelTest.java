package io.genfin.refund.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceModelTest {

  @Test
  void paymentInvoiceAndCustomerConvenienceFactories() {
    Reference paymentRef = Reference.payment("PAY-1");
    Reference invoiceRef = Reference.invoice("INV-1");
    Reference customerRef = Reference.customer("CUST-1");

    assertThat(paymentRef.type()).isEqualTo(StandardReferenceType.PAYMENT);
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
                Reference.payment("PAY-1"),
                Reference.invoice("INV-1"),
                Reference.customer("CUST-1"),
                Reference.invoice("INV-2")));

    assertThat(collection.byType(StandardReferenceType.INVOICE)).hasSize(2);
    assertThat(collection.byType(StandardReferenceType.CUSTOMER)).hasSize(1);
    assertThat(collection.byType(StandardReferenceType.PAYMENT)).hasSize(1);
  }

  @Test
  void emptyCollectionReportsEmptyAndAddReturnsANewInstance() {
    ReferenceCollection empty = ReferenceCollection.empty();

    assertThat(empty.isEmpty()).isTrue();

    ReferenceCollection withOne = empty.add(Reference.payment("PAY-1"));

    assertThat(withOne.isEmpty()).isFalse();
    assertThat(empty.isEmpty()).isTrue();
  }
}
