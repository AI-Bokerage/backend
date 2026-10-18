package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.util.UUID;

@Schema(description = "Invoice payment request")
public record MarkInvoicePaidRequest(
    @Min(0)
    Long amountPaid,
    @Schema(description = "Id of the user recording this payment, for the audit trail. Same "
        + "client-supplied-actor convention as RefundController/CreditNoteController.")
    UUID actorId
) {
}
