package com.company.bsmsvc.domain.model;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceLineItemTest {

    @Test
    void testValidLineItem() {
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(UUID.randomUUID())
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Valid item")
            .quantity(3)
            .unitAmountMinor(100L)
            .amountMinor(300L)
            .build();

        assertThatNoException().isThrownBy(item::validate);
    }

    @Test
    void testNegativeQuantityThrows() {
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(UUID.randomUUID())
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Negative quantity")
            .quantity(-1)
            .unitAmountMinor(100L)
            .amountMinor(-100L)
            .build();

        assertThatThrownBy(item::validate)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Quantity cannot be negative: -1");
    }

    @Test
    void testAmountMinorMismatchThrows() {
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(UUID.randomUUID())
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Mismatch amount")
            .quantity(2)
            .unitAmountMinor(100L)
            .amountMinor(150L) // should be 200
            .build();

        assertThatThrownBy(item::validate)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Amount minor (150) must be derived from quantity * unitAmountMinor (200)");
    }

    @Test
    void testNullItemTypeThrows() {
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(UUID.randomUUID())
            .itemType(null)
            .description("No item type")
            .quantity(2)
            .unitAmountMinor(100L)
            .amountMinor(200L)
            .build();

        assertThatThrownBy(item::validate)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Item type cannot be null");
    }

    @Test
    void testBlankDescriptionThrows() {
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(UUID.randomUUID())
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("   ")
            .quantity(2)
            .unitAmountMinor(100L)
            .amountMinor(200L)
            .build();

        assertThatThrownBy(item::validate)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Description cannot be null or empty");
    }
}
