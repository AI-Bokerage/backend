package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.AddPaymentMethodRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.dto.response.PaymentMethodResponse;
import com.company.bsmsvc.api.dto.response.PaymentMethodSummaryResponse;
import com.company.bsmsvc.api.mapper.PaymentMethodApiMapper;
import com.company.bsmsvc.application.service.PaymentMethodService;
import com.company.bsmsvc.domain.model.PaymentMethod;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/payment-methods")
@Tag(name = "Payment Methods", description = "Manage saved payment methods per tenant")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;
    private final PaymentMethodApiMapper mapper;

    public PaymentMethodController(PaymentMethodService paymentMethodService, PaymentMethodApiMapper mapper) {
        this.paymentMethodService = paymentMethodService;
        this.mapper = mapper;
    }

    @RequirePermission("billing.write")
    @PostMapping
    @Operation(summary = "Add a payment method for a tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Payment method added"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate or business rule violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> addPaymentMethod(
        @Valid @RequestBody AddPaymentMethodRequest request
    ) {
        PaymentMethod pm = paymentMethodService.addPaymentMethod(
            request.tenantId(), request.paymentMethodToken(), request.type(),
            request.brand(), request.lastFour(), request.expMonth(), request.expYear(), request.makeDefault()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Payment method added successfully", mapper.toResponse(pm)));
    }

    @RequirePermission("billing.read")
    @GetMapping
    @Operation(summary = "List payment methods for a tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment methods returned")
    })
    public ResponseEntity<ApiResponse<List<PaymentMethodSummaryResponse>>> listPaymentMethods(
        @Parameter(description = "Tenant ID", required = true) @RequestParam UUID tenantId
    ) {
        List<PaymentMethodSummaryResponse> responses = paymentMethodService.listPaymentMethods(tenantId)
            .stream().map(mapper::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.ok("Payment methods fetched successfully", responses));
    }

    @RequirePermission("billing.write")
    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a payment method")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Payment method removed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment method not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> removePaymentMethod(@PathVariable UUID id) {
        paymentMethodService.removePaymentMethod(id);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission("billing.write")
    @PatchMapping("/{id}/default")
    @Operation(summary = "Set a payment method as the default for its tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Default updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment method not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> setDefault(@PathVariable UUID id) {
        PaymentMethod pm = paymentMethodService.setDefaultPaymentMethod(id);
        return ResponseEntity.ok(ApiResponse.ok("Default payment method updated", mapper.toResponse(pm)));
    }
}
