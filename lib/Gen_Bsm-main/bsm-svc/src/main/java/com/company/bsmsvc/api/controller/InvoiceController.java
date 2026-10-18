package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.CreateInvoiceRequest;
import com.company.bsmsvc.api.dto.request.MarkInvoicePaidRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.InvoiceResponse;
import com.company.bsmsvc.api.dto.response.InvoicePdfStatusResponse;
import com.company.bsmsvc.api.dto.response.PaginationMetadata;
import com.company.bsmsvc.api.mapper.InvoiceApiMapper;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.util.PaginationUtils;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.InvoicePdfStatus;
import com.company.bsmsvc.domain.model.InvoiceFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.storage.InvoiceDocumentAccessService;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoice Management", description = "Invoice generation and lifecycle endpoints")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceApiMapper invoiceApiMapper;
    private final InvoiceDocumentAccessService invoiceDocumentAccessService;

    @RequirePermission("billing.write")
    @PostMapping
    @Operation(summary = "Generate a new invoice")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Invoice generated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        PlatformInvoice invoice = invoiceApiMapper.toDomain(request);
        InvoiceResponse response = invoiceApiMapper.toResponse(invoiceService.createInvoice(invoice));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Invoice generated successfully", response));
    }

    @RequirePermission("billing.read")
    @GetMapping
    @Operation(summary = "Search invoices")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoices fetched successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid query parameters", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> searchInvoices(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) UUID subscriptionId,
        @RequestParam(required = false) String invoiceNumber,
        @RequestParam(required = false) InvoiceStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        PageResult<PlatformInvoice> result = invoiceService.searchInvoices(
            new InvoiceFilter(tenantId, subscriptionId, invoiceNumber, status),
            PaginationUtils.clampPage(page),
            PaginationUtils.clampSize(size),
            sort,
            direction
        );
        List<InvoiceResponse> data = result.content().stream().map(invoiceApiMapper::toResponse).toList();
        PaginationMetadata pagination = new PaginationMetadata(
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.ok("Invoices fetched successfully", data, pagination));
    }

    @RequirePermission("billing.read")
    @GetMapping("/{id}")
    @Operation(summary = "Get invoice by id")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice fetched successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceById(@PathVariable UUID id) {
        InvoiceResponse response = invoiceApiMapper.toResponse(invoiceService.getInvoiceById(id));
        return ResponseEntity.ok(ApiResponse.ok("Invoice fetched successfully", response));
    }

    @RequirePermission("billing.read")
    @GetMapping("/{id}/pdf")
    @Operation(summary = "Get invoice PDF status and URL")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice PDF status fetched successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<InvoicePdfStatusResponse>> getInvoicePdf(@PathVariable UUID id) {
        PlatformInvoice invoice = invoiceService.getInvoiceById(id);
        String status = invoice.getPdfGenerationStatus() == null ? InvoicePdfStatus.PENDING.name() : invoice.getPdfGenerationStatus().name();
        String preSignedUrl = null;
        String expiresAt = null;

        if (invoice.getPdfUrl() != null && InvoicePdfStatus.GENERATED.name().equals(status)) {
            var access = invoiceDocumentAccessService.getPreSignedUrl(invoice);
            preSignedUrl = access.getPreSignedUrl();
            expiresAt = access.getExpiresAt();
        }

        InvoicePdfStatusResponse resp = new InvoicePdfStatusResponse(
            status,
            invoice.getPdfUrl(),
            preSignedUrl,
            expiresAt
        );
        return ResponseEntity.ok(ApiResponse.ok("Invoice pdf status fetched", resp));
    }

    @RequirePermission("billing.write")
    @PostMapping("/{id}/payments")
    @Operation(summary = "Mark invoice payment")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice payment processed successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<InvoiceResponse>> markInvoicePaid(
        @PathVariable UUID id,
        @Valid @RequestBody MarkInvoicePaidRequest request
    ) {
        InvoiceResponse response = invoiceApiMapper.toResponse(
            invoiceService.applyPayment(id, request.amountPaid(), request.actorId()));
        return ResponseEntity.ok(ApiResponse.ok("Invoice payment processed successfully", response));
    }

    @RequirePermission("billing.admin")
    @PostMapping("/{id}/void")
    @Operation(summary = "Void an invoice")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice voided successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<InvoiceResponse>> voidInvoice(
        @PathVariable UUID id,
        @RequestParam(required = false) UUID actorId
    ) {
        InvoiceResponse response = invoiceApiMapper.toResponse(invoiceService.voidInvoice(id, actorId));
        return ResponseEntity.ok(ApiResponse.ok("Invoice voided successfully", response));
    }
}
