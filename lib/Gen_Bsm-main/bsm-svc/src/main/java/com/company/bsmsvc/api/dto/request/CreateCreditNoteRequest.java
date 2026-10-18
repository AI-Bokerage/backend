package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@Schema(description = "Create credit note request")
public record CreateCreditNoteRequest(
    @NotNull @Schema(description = "Tenant ID") UUID tenantId,
    @NotNull @Schema(description = "Invoice to which this credit note applies") UUID invoiceId,
    @Positive @Schema(description = "Credit amount in minor units (e.g. paise for INR)") long amountMinor,
    @NotBlank @Schema(description = "ISO 4217 currency code") String currency,
    @NotBlank @Schema(description = "Reason for issuing the credit note") String reason,
    @NotNull @Schema(description = "ID of the user creating the credit note") UUID createdBy
) {}
