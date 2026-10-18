package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.CreateRefundRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.CreditNoteResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.mapper.CreditNoteApiMapper;
import com.company.bsmsvc.application.service.RefundService;
import com.company.bsmsvc.domain.model.CreditNote;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/refunds")
@Tag(name = "Refunds", description = "Initiate refunds for paid invoices")
public class RefundController {

    private final RefundService refundService;
    private final CreditNoteApiMapper creditNoteApiMapper;

    public RefundController(RefundService refundService, CreditNoteApiMapper creditNoteApiMapper) {
        this.refundService = refundService;
        this.creditNoteApiMapper = creditNoteApiMapper;
    }

    @RequirePermission("billing.admin")
    @PostMapping
    @Operation(summary = "Create a refund for a paid invoice")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Refund created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invoice not paid or amount exceeds paid amount",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Provider refund failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<CreditNoteResponse>> createRefund(
        @Valid @RequestBody CreateRefundRequest request
    ) {
        CreditNote creditNote = refundService.createRefund(
            request.tenantId(), request.invoiceId(), request.amountMinor(),
            request.reason(), request.requestedBy()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Refund created", creditNoteApiMapper.toResponse(creditNote)));
    }
}
