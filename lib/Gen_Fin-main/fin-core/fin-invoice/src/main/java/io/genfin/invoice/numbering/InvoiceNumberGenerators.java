package io.genfin.invoice.numbering;

import io.genfin.invoice.internal.numbering.DefaultNumberFormatter;
import io.genfin.invoice.internal.numbering.InMemorySequenceProvider;
import io.genfin.invoice.internal.numbering.SequentialInvoiceNumberGenerator;
import io.genfin.invoice.internal.numbering.TimestampInvoiceNumberGenerator;
import io.genfin.invoice.internal.numbering.UuidInvoiceNumberGenerator;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;
import io.genfin.invoice.port.numbering.NumberFormatter;
import io.genfin.invoice.port.numbering.SequenceProvider;

/**
 * Factory for {@link InvoiceNumberGenerator}s. Consumers must obtain generators here, not
 * implementations directly.
 */
public final class InvoiceNumberGenerators {

  private static final NumberFormatter DEFAULT_FORMATTER = new DefaultNumberFormatter();
  private static final InvoiceNumberGenerator TIMESTAMP = new TimestampInvoiceNumberGenerator();
  private static final InvoiceNumberGenerator UUID = new UuidInvoiceNumberGenerator();

  private InvoiceNumberGenerators() {}

  public static InvoiceNumberGenerator sequential(NumberTemplate template) {
    return sequential(template, new InMemorySequenceProvider(), DEFAULT_FORMATTER);
  }

  public static InvoiceNumberGenerator sequential(
      NumberTemplate template, SequenceProvider sequenceProvider, NumberFormatter formatter) {
    return new SequentialInvoiceNumberGenerator(template, sequenceProvider, formatter);
  }

  public static InvoiceNumberGenerator timestamp() {
    return TIMESTAMP;
  }

  public static InvoiceNumberGenerator uuid() {
    return UUID;
  }

  public static NumberFormatter defaultFormatter() {
    return DEFAULT_FORMATTER;
  }

  public static SequenceProvider inMemorySequence() {
    return new InMemorySequenceProvider();
  }
}
