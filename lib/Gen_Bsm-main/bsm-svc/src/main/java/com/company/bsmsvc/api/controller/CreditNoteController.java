package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.CreateCreditNoteRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.CreditNoteResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.dto.response.PaginationMetadata;
import com.company.bsmsvc.api.mapper.CreditNoteApiMapper;
import com.company.bsmsvc.application.service.CreditNoteService;
import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.PageResult;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/bsm/credit-notes")
@Tag(name = "Credit Notes", description = "Credit note lifecycle endpoints")
public class CreditNoteController {

    private final CreditNoteService creditNoteService;
    private final CreditNoteApiMapper creditNoteApiMapper;

    public CreditNoteController(CreditNoteService creditNoteService, CreditNoteApiMapper creditNoteApiMapper) {
        this.creditNoteService = creditNoteService;
        this.creditNoteApiMapper = creditNoteApiMapper;
    }

    @RequirePermission("billing.admin")
    @PostMapping
    @Operation(summary = "Issue a new credit note")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Credit note created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<CreditNoteResponse>> create(@Valid @RequestBody CreateCreditNoteRequest req) {
        CreditNote cn = creditNoteService.createCreditNote(
            req.tenantId(), req.invoiceId(), req.amountMinor(), req.currency(), req.reason(), req.createdBy()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Credit note created successfully", creditNoteApiMapper.toResponse(cn)));
    }

    @RequirePermission("billing.read")
    @GetMapping
    @Operation(summary = "List credit notes with filtering and pagination")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Credit notes fetched")
    })
    public ResponseEntity<ApiResponse<List<CreditNoteResponse>>> list(
        @Parameter(description = "Filter by tenant") @RequestParam(required = false) UUID tenantId,
        @Parameter(description = "Filter by invoice") @RequestParam(required = false) UUID invoiceId,
        @Parameter(description = "Filter by status") @RequestParam(required = false) CreditNoteStatus status,
        @Parameter(description = "Filter by credit number") @RequestParam(required = false) String creditNumber,
        @Parameter(description = "Filter from date (ISO-8601)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
        @Parameter(description = "Filter to date (ISO-8601)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
        @Parameter(description = "Page index (0-based)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Sort direction: ASC or DESC") @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        CreditNoteFilter filter = new CreditNoteFilter(tenantId, invoiceId, status, creditNumber, createdFrom, createdTo);
        PageResult<CreditNote> result = creditNoteService.listCreditNotes(filter, page, size, sortBy, sortDirection);
        List<CreditNoteResponse> responses = result.content().stream().map(creditNoteApiMapper::toResponse).toList();
        PaginationMetadata pagination = new PaginationMetadata(
            result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.ok("Credit notes fetched successfully", responses, pagination));
    }

    @RequirePermission("billing.read")
    @GetMapping("/{id}")
    @Operation(summary = "Get credit note by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Credit note found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Credit note not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<CreditNoteResponse>> get(@PathVariable UUID id) {
        CreditNote cn = creditNoteService.getCreditNoteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Credit note fetched successfully", creditNoteApiMapper.toResponse(cn)));
    }

    @RequirePermission("billing.admin")
    @PostMapping("/{id}/apply")
    @Operation(summary = "Apply a credit note to reduce outstanding balance")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Credit note applied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Credit note not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<CreditNoteResponse>> apply(@PathVariable UUID id) {
        CreditNote cn = creditNoteService.applyCreditNote(id);
        return ResponseEntity.ok(ApiResponse.ok("Credit note applied successfully", creditNoteApiMapper.toResponse(cn)));
    }

    @RequirePermission("billing.admin")
    @PostMapping("/{id}/void")
    @Operation(summary = "Void an open credit note")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Credit note voided"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Credit note not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<CreditNoteResponse>> voidCredit(@PathVariable UUID id) {
        CreditNote cn = creditNoteService.voidCreditNote(id);
        return ResponseEntity.ok(ApiResponse.ok("Credit note voided successfully", creditNoteApiMapper.toResponse(cn)));
    }
}
