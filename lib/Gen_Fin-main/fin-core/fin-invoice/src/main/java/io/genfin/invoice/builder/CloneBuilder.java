package io.genfin.invoice.builder;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.line.InvoiceLine;
import io.genfin.invoice.line.InvoiceLineBuilder;
import io.genfin.invoice.reference.Reference;

/**
 * Duplicates an invoice's content (lines, discounts, references, metadata, tags) into a brand-new
 * draft — a fresh identity, no lifecycle history, no adjustments, no payments, no attachments.
 */
public final class CloneBuilder {

  private CloneBuilder() {}

  public static Invoice clone(Invoice source, ClockProvider clockProvider, String actor) {
    Invoice cloned = CopyBuilder.from(source, clockProvider, actor).build();

    for (InvoiceLine line : source.lines()) {
      cloned.addLine(recreate(line), clockProvider, actor);
    }
    source.discounts().forEach(discount -> cloned.addDiscount(discount, clockProvider, actor));
    source.tags().forEach(tag -> cloned.addTag(tag, clockProvider, actor));
    for (Reference reference : source.references().all()) {
      cloned.addReference(reference, clockProvider, actor);
    }
    cloned.updateMetadata(source.metadata(), clockProvider, actor);
    cloned.updateAttributes(source.attributes(), clockProvider, actor);
    cloned.updateExtensionProperties(source.extensionProperties(), clockProvider, actor);
    return cloned;
  }

  private static InvoiceLine recreate(InvoiceLine line) {
    InvoiceLineBuilder builder =
        InvoiceLineBuilder.newLine()
            .description(line.description())
            .quantity(line.quantity())
            .unitPrice(line.unitPrice())
            .discount(line.discount())
            .taxBreakdown(line.taxBreakdown())
            .metadata(line.metadata())
            .attributes(line.attributes());
    line.references().all().forEach(builder::reference);
    return builder.build();
  }
}
