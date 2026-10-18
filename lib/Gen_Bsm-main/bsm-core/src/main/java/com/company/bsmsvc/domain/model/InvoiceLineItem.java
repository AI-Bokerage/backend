package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceLineItem {

    private UUID id;
    private UUID invoiceId;
    private InvoiceLineItemType itemType;
    private String description;
    private int quantity;
    private long unitAmountMinor;
    private long amountMinor;
    private Map<String, Object> metadata;
    private Instant createdAt;

    /**
     * Domain validation for line items.
     * Prevents negative quantity, invalid amounts, and uninitialized fields.
     */
    public void validate() {
        if (quantity < 0) {
            throw new BusinessRuleViolationException("Quantity cannot be negative: " + quantity);
        }
        long expectedAmount = (long) quantity * unitAmountMinor;
        if (amountMinor != expectedAmount) {
            throw new BusinessRuleViolationException("Amount minor (" + amountMinor 
                + ") must be derived from quantity * unitAmountMinor (" + expectedAmount + ")");
        }
        if (itemType == null) {
            throw new BusinessRuleViolationException("Item type cannot be null");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleViolationException("Description cannot be null or empty");
        }
    }
}
