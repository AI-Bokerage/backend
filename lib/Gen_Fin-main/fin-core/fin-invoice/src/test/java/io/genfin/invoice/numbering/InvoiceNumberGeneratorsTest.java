package io.genfin.invoice.numbering;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvoiceNumberGeneratorsTest {

  private static final NumberGenerationContext CONTEXT =
      new NumberGenerationContext(Instant.parse("2026-03-15T00:00:00Z"), "acme", USD);

  @Test
  void sequentialGeneratorFormatsYearAndPaddedSequence() {
    var generator = InvoiceNumberGenerators.sequential(NumberTemplate.of("INV-{YEAR}-{SEQ}"));

    assertThat(generator.generate(CONTEXT).value()).isEqualTo("INV-2026-000001");
    assertThat(generator.generate(CONTEXT).value()).isEqualTo("INV-2026-000002");
  }

  @Test
  void sequencesArePartitionedByScopeKey() {
    var generator = InvoiceNumberGenerators.sequential(NumberTemplate.of("{SEQ}"));

    var contextA = new NumberGenerationContext(CONTEXT.asOf(), "tenant-a", USD);
    var contextB = new NumberGenerationContext(CONTEXT.asOf(), "tenant-b", USD);

    assertThat(generator.generate(contextA).value()).isEqualTo("000001");
    assertThat(generator.generate(contextB).value()).isEqualTo("000001");
    assertThat(generator.generate(contextA).value()).isEqualTo("000002");
  }

  @Test
  void timestampGeneratorProducesUniqueValues() {
    var generator = InvoiceNumberGenerators.timestamp();

    assertThat(generator.generate(CONTEXT).value()).startsWith("INV-20260315");
  }

  @Test
  void uuidGeneratorProducesDistinctValues() {
    var generator = InvoiceNumberGenerators.uuid();

    assertThat(generator.generate(CONTEXT)).isNotEqualTo(generator.generate(CONTEXT));
  }
}
