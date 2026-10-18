package io.genfin.invoice.builder;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.invoice.factory.LineFactory;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.invoice.InvoiceBuilder;
import io.genfin.invoice.reference.StandardReferenceType;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CopyCloneRevisionBuilderTest {

  private static Invoice sourceInvoice() {
    Invoice invoice =
        InvoiceBuilder.newInvoice()
            .currency(USD)
            .dueDate(Instant.parse("2026-02-01T00:00:00Z"))
            .clockProvider(ClockProviders.system())
            .actor("tester")
            .build();
    invoice.addLine(
        LineFactory.simple("Item", BigDecimal.ONE, Money.of("10.00", USD)),
        ClockProviders.system(),
        "t");
    invoice.addTag("priority", ClockProviders.system(), "t");
    return invoice;
  }

  @Test
  void copyBuilderStartsAnEmptyDraftWithSameHeader() {
    Invoice source = sourceInvoice();

    Invoice copy = CopyBuilder.from(source, ClockProviders.system(), "t").build();

    assertThat(copy.currency()).isEqualTo(source.currency());
    assertThat(copy.id()).isNotEqualTo(source.id());
    assertThat(copy.lines()).isEmpty();
  }

  @Test
  void cloneBuilderDuplicatesLinesAndTags() {
    Invoice source = sourceInvoice();

    Invoice cloned = CloneBuilder.clone(source, ClockProviders.system(), "t");

    assertThat(cloned.id()).isNotEqualTo(source.id());
    assertThat(cloned.lines()).hasSize(1);
    assertThat(cloned.lines().get(0).description()).isEqualTo("Item");
    assertThat(cloned.tags()).contains("priority");
  }

  @Test
  void revisionBuilderLinksBackToOriginalViaReference() {
    Invoice source = sourceInvoice();
    var revisionType = StandardReferenceType.of("REVISION_OF");

    Invoice revision = RevisionBuilder.reviseOf(source, revisionType, ClockProviders.system(), "t");

    assertThat(revision.references().byType(revisionType)).hasSize(1);
    assertThat(revision.references().byType(revisionType).get(0).value().value())
        .isEqualTo(source.id().value());
  }
}
