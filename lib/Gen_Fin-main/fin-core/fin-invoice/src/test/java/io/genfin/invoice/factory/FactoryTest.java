package io.genfin.invoice.factory;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.metadata.Metadata;
import io.genfin.invoice.numbering.NumberGenerationContext;
import io.genfin.invoice.reference.Reference;
import io.genfin.invoice.spi.InvoiceExtensions;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FactoryTest {

  @Test
  void invoiceFactoryResolvesRegisteredDefaults() {
    ExtensionRegistry registry = ExtensionRegistries.create();
    InvoiceExtensions.registerDefaults(registry);

    var builder =
        InvoiceFactory.using(registry)
            .newDraft(USD, Instant.parse("2026-02-01T00:00:00Z"), "tester");
    var invoice = builder.build();

    assertThat(invoice.currency()).isEqualTo(USD);

    var number =
        InvoiceFactory.using(registry)
            .nextNumber(new NumberGenerationContext(Instant.now(), "acme", USD));
    assertThat(number.value()).isNotBlank();
  }

  @Test
  void lineFactoryBuildsSimpleAndDiscountedLines() {
    var simple = LineFactory.simple("Item", BigDecimal.ONE, Money.of("10.00", USD));
    var discounted =
        LineFactory.discounted(
            "Item",
            BigDecimal.ONE,
            Money.of("10.00", USD),
            DiscountFactory.fixed(Money.of("1.00", USD), "r"));

    assertThat(simple.netAmount()).isEqualTo(Money.of("10.00", USD));
    assertThat(discounted.netAmount()).isEqualTo(Money.of("9.00", USD));
  }

  @Test
  void adjustmentFactoryEnforcesSignConvention() {
    assertThat(AdjustmentFactory.credit(Money.of("5.00", USD), "r").amount())
        .isEqualTo(Money.of("-5.00", USD));
    assertThat(AdjustmentFactory.debit(Money.of("5.00", USD), "r").amount())
        .isEqualTo(Money.of("5.00", USD));
    assertThat(AdjustmentFactory.credit(Money.of("-5.00", USD), "r").amount())
        .isEqualTo(Money.of("-5.00", USD));
  }

  @Test
  void discountFactoryBuildsEachStandardType() {
    Discount coupon = DiscountFactory.coupon(Money.of("5.00", USD), "SAVE5");

    assertThat(coupon.fixedAmount()).isEqualTo(Money.of("5.00", USD));
    assertThat(coupon.reason()).isEqualTo("SAVE5");
  }

  @Test
  void referenceFactoryBuildsAdHocTypes() {
    Reference reference = ReferenceFactory.of("SUBSCRIPTION", "SUB-1");

    assertThat(reference.type().code()).isEqualTo("SUBSCRIPTION");
  }

  @Test
  void metadataFactoryAutoDetectsTypedValueKind() {
    Metadata metadata = MetadataFactory.fromValues(Map.of("name", "Acme", "active", true));

    assertThat(metadata.find("name"))
        .get()
        .satisfies(v -> assertThat(v.asString()).isEqualTo("Acme"));
    assertThat(metadata.find("active")).get().satisfies(v -> assertThat(v.asBoolean()).isTrue());
  }
}
