package io.genfin.invoice.line;

import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.id.LineId;
import io.genfin.invoice.metadata.Attributes;
import io.genfin.invoice.metadata.Metadata;
import io.genfin.invoice.reference.Reference;
import io.genfin.invoice.reference.ReferenceCollection;
import io.genfin.money.money.Money;
import io.genfin.money.tax.TaxBreakdown;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link InvoiceLine}s. Consumers must not call the {@code InvoiceLine} constructor
 * directly.
 */
public final class InvoiceLineBuilder {

  private LineId id;
  private String description;
  private BigDecimal quantity;
  private Money unitPrice;
  private Discount discount;
  private TaxBreakdown taxBreakdown;
  private Metadata metadata;
  private final List<Reference> references = new ArrayList<>();
  private Attributes attributes;

  private InvoiceLineBuilder() {}

  public static InvoiceLineBuilder newLine() {
    return new InvoiceLineBuilder();
  }

  public InvoiceLineBuilder id(LineId id) {
    this.id = id;
    return this;
  }

  public InvoiceLineBuilder description(String description) {
    this.description = description;
    return this;
  }

  public InvoiceLineBuilder quantity(BigDecimal quantity) {
    this.quantity = quantity;
    return this;
  }

  public InvoiceLineBuilder unitPrice(Money unitPrice) {
    this.unitPrice = unitPrice;
    return this;
  }

  public InvoiceLineBuilder discount(Discount discount) {
    this.discount = discount;
    return this;
  }

  public InvoiceLineBuilder taxBreakdown(TaxBreakdown taxBreakdown) {
    this.taxBreakdown = taxBreakdown;
    return this;
  }

  public InvoiceLineBuilder metadata(Metadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public InvoiceLineBuilder reference(Reference reference) {
    this.references.add(reference);
    return this;
  }

  public InvoiceLineBuilder attributes(Attributes attributes) {
    this.attributes = attributes;
    return this;
  }

  public InvoiceLine build() {
    return new InvoiceLine(
        id == null ? LineId.generate() : id,
        description,
        quantity,
        unitPrice,
        discount,
        taxBreakdown,
        metadata,
        ReferenceCollection.of(references),
        attributes);
  }
}
