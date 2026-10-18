package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.CreateCheckoutSessionRequest;
import com.company.bsmsvc.api.dto.request.CreatePaymentIntentRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.dto.response.PaymentResponse;
import com.company.bsmsvc.api.mapper.PaymentApiMapper;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.payment.PaymentIntentResult;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/payments")
@Tag(name = "Payments", description = "Initiate payments for invoices via Stripe or Razorpay")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentApiMapper mapper;

    public PaymentController(PaymentService paymentService, PaymentApiMapper mapper) {
        this.paymentService = paymentService;
        this.mapper = mapper;
    }

    @RequirePermission("billing.write")
    @PostMapping("/checkout-session")
    @Operation(summary = "Create a checkout session for an invoice")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Checkout session created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or invoice not payable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Provider error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> createCheckoutSession(
        @Valid @RequestBody CreateCheckoutSessionRequest request
    ) {
        CheckoutSessionResult result = paymentService.createCheckoutSession(
            request.tenantId(), request.invoiceId(), request.successUrl(), request.cancelUrl()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Checkout session created",
            Map.of("sessionId", result.sessionId(), "checkoutUrl", result.checkoutUrl())));
    }

    @RequirePermission("billing.write")
    @PostMapping("/payment-intents")
    @Operation(summary = "Create a payment intent for an invoice")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Payment intent created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invoice not payable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> createPaymentIntent(
        @Valid @RequestBody CreatePaymentIntentRequest request
    ) {
        PaymentIntentResult result = paymentService.createPaymentIntent(request.tenantId(), request.invoiceId());
        var data = result.clientSecret() != null
            ? Map.of("paymentIntentId", result.paymentIntentId(), "clientSecret", result.clientSecret(), "status", result.status())
            : Map.of("paymentIntentId", result.paymentIntentId(), "status", result.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Payment intent created", data));
    }

    @RequirePermission("billing.read")
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment record by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.ok("Payment fetched", mapper.toResponse(paymentService.getPayment(paymentId))));
    }
}
