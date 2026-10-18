package io.genfin.invoice.internal.numbering;

import io.genfin.invoice.numbering.InvoiceNumber;
import io.genfin.invoice.numbering.NumberGenerationContext;
import io.genfin.invoice.numbering.NumberTemplate;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;
import io.genfin.invoice.port.numbering.NumberFormatter;
import io.genfin.invoice.port.numbering.SequenceProvider;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * {@code NumberTemplate} + {@code SequenceProvider} + {@code NumberFormatter} composed into a
 * sequential scheme.
 */
public final class SequentialInvoiceNumberGenerator implements InvoiceNumberGenerator {

  private final NumberTemplate template;
  private final SequenceProvider sequenceProvider;
  private final NumberFormatter formatter;

  public SequentialInvoiceNumberGenerator(
      NumberTemplate template, SequenceProvider sequenceProvider, NumberFormatter formatter) {
    this.template = template;
    this.sequenceProvider = sequenceProvider;
    this.formatter = formatter;
  }

  @Override
  public InvoiceNumber generate(NumberGenerationContext context) {
    String scopeKey = context.scopeKey() == null ? "default" : context.scopeKey();
    long sequence = sequenceProvider.next(scopeKey);
    String year =
        DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC).format(context.asOf());
    Map<String, String> tokens =
        Map.of(
            "YEAR",
            year,
            "SEQ",
            String.format("%06d", sequence),
            "CURRENCY",
            context.currency() == null ? "" : context.currency().code());
    return InvoiceNumber.of(formatter.format(template, tokens));
  }
}
