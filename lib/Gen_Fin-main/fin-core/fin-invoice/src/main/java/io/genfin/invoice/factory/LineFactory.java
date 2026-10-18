package io.genfin.invoice.factory;

import io.genfin.invoice.discount.Discount;
import io.genfin.invoice.line.InvoiceLine;
import io.genfin.invoice.line.InvoiceLineBuilder;
import io.genfin.money.money.Money;
import java.math.BigDecimal;

/** Convenience constructors for the common {@link InvoiceLine} shapes. */
public final class LineFactory {

  private LineFactory() {}

  public static InvoiceLine simple(String description, BigDecimal quantity, Money unitPrice) {
    return InvoiceLineBuilder.newLine()
        .description(description)
        .quantity(quantity)
        .unitPrice(unitPrice)
        .build();
  }

  public static InvoiceLine discounted(
      String description, BigDecimal quantity, Money unitPrice, Discount discount) {
    return InvoiceLineBuilder.newLine()
        .description(description)
        .quantity(quantity)
        .unitPrice(unitPrice)
        .discount(discount)
        .build();
  }
}
