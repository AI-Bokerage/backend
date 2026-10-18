package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Schema(description = "Invoice line item payload")
public record CreateInvoiceLineItemRequest(
    @NotNull
    InvoiceLineItemType itemType,

    @NotBlank
    String description,

    @Min(0)
    int quantity,

    @Min(0)
    long unitAmountMinor,

    @Min(0)
    long amountMinor,

    Map<String, Object> metadata
) {
}
