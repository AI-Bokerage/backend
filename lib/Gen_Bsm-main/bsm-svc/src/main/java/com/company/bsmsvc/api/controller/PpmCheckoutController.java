package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.PpmInitiateCheckoutRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.dto.response.PpmCheckoutResponse;
import com.company.bsmsvc.application.service.PpmCheckoutService;
import com.company.bsmsvc.domain.model.CheckoutResult;
import com.company.bsmsvc.domain.model.InitiateCheckoutCommand;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/bsm/checkout")
@RequiredArgsConstructor
@Tag(name = "PPM Checkout", description = "PPM-backed subscription checkout")
public class PpmCheckoutController {

    private final PpmCheckoutService ppmCheckoutService;

    @RequirePermission("billing.write")
    @PostMapping("/ppm-initiate")
    @Operation(summary = "Initiate PPM-backed checkout",
        description = "Resolves price and promo from PPM-SVC, creates a subscription + invoice, "
            + "and returns a payment provider checkout URL.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
        description = "Checkout session initiated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
        description = "Validation error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
        description = "Promo code not applicable or business rule violation",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
        description = "PPM service unavailable or circuit breaker open",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ApiResponse<PpmCheckoutResponse>> initiateCheckout(
        @Valid @RequestBody PpmInitiateCheckoutRequest request
    ) {
        log.debug("→ POST /api/v1/bsm/checkout/ppm-initiate tenantId={} ppmPlanId={}",
            request.tenantId(), request.ppmPlanId());
        InitiateCheckoutCommand command = new InitiateCheckoutCommand(
            request.tenantId(), request.ppmPlanId(), request.billingCycle(), request.region(),
            request.promoCode(), request.successUrl(), request.cancelUrl(),
            request.performedBy(), request.reason());
        CheckoutResult result = ppmCheckoutService.initiateCheckout(command);
        PpmCheckoutResponse response = new PpmCheckoutResponse(
            result.subscriptionId(), result.invoiceId(), result.checkoutUrl(), result.sessionId(),
            result.resolvedAmountMinor(), result.currency(), result.discountAmountMinor(),
            result.ppmPlanVersionId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("PPM checkout session initiated successfully", response));
    }
}
