package io.genfin.invoice.calculation;

import io.genfin.money.money.Money;
import io.genfin.money.tax.TaxBreakdown;

/**
 * Every total an invoice can report — always {@code Money}, always produced by an {@code
 * InvoiceCalculator}.
 */
public record InvoiceCalculationResult(
    Money subtotal,
    Money discountTotal,
    Money adjustmentTotal,
    TaxBreakdown taxBreakdown,
    Money grandTotal,
    Money amountPaid,
    Money balanceDue) {}
