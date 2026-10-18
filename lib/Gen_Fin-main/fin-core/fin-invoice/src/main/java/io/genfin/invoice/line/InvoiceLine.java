package io.genfin.invoice.line;

import io.genfin.api.domain.Entity;
import io.genfin.api.validation.Validate;
import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.discount.DiscountEngines;
import io.genfin.invoice.id.LineId;
import io.genfin.invoice.metadata.Attributes;
import io.genfin.invoice.metadata.Metadata;
import io.genfin.invoice.port.discount.DiscountEngine;
import io.genfin.invoice.reference.ReferenceCollection;
import io.genfin.money.money.Money;
import io.genfin.money.tax.TaxBreakdown;
import java.math.BigDecimal;

/**
 * A single invoice line. Calculates its own net/gross amount — {@code Invoice} only sums lines,
 * never recomputes their internals.
 */
public final class InvoiceLine extends Entity<LineId> {

  private final String description;
  private final BigDecimal quantity;
  private final Money unitPrice;
  private final Discount discount;
  private final TaxBreakdown taxBreakdown;
  private final Metadata metadata;
  private final ReferenceCollection references;
  private final Attributes attributes;

  InvoiceLine(
      LineId id,
      String description,
      BigDecimal quantity,
      Money unitPrice,
      Discount discount,
      TaxBreakdown taxBreakdown,
      Metadata metadata,
      ReferenceCollection references,
      Attributes attributes) {
    super(id);
    this.description = Validate.notBlank(description, "description must not be blank.");
    this.quantity = Validate.notNull(quantity, "quantity must not be null.");
    Validate.argument(quantity.signum() > 0, "quantity must be positive.");
    this.unitPrice = Validate.notNull(unitPrice, "unitPrice must not be null.");
    Validate.argument(!unitPrice.isNegative(), "unitPrice must not be negative.");
    this.discount = discount;
    this.taxBreakdown =
        taxBreakdown == null ? TaxBreakdown.none(unitPrice.currency()) : taxBreakdown;
    this.metadata = metadata == null ? Metadata.empty() : metadata;
    this.references = references == null ? ReferenceCollection.empty() : references;
    this.attributes = attributes == null ? Attributes.empty() : attributes;
  }

  public String description() {
    return description;
  }

  public BigDecimal quantity() {
    return quantity;
  }

  public Money unitPrice() {
    return unitPrice;
  }

  public Discount discount() {
    return discount;
  }

  public TaxBreakdown taxBreakdown() {
    return taxBreakdown;
  }

  public Metadata metadata() {
    return metadata;
  }

  public ReferenceCollection references() {
    return references;
  }

  public Attributes attributes() {
    return attributes;
  }

  public Money grossListAmount() {
    return unitPrice.multiply(quantity);
  }

  public Money discountAmount() {
    return discountAmount(DiscountEngines.standard());
  }

  public Money discountAmount(DiscountEngine discountEngine) {
    return discount == null
        ? Money.zero(unitPrice.currency())
        : discountEngine.apply(discount, grossListAmount());
  }

  public Money netAmount() {
    return netAmount(DiscountEngines.standard());
  }

  public Money netAmount(DiscountEngine discountEngine) {
    return grossListAmount().subtract(discountAmount(discountEngine));
  }

  public Money totalAmount() {
    return totalAmount(DiscountEngines.standard());
  }

  public Money totalAmount(DiscountEngine discountEngine) {
    return netAmount(discountEngine).add(taxBreakdown.totalTax());
  }
}
