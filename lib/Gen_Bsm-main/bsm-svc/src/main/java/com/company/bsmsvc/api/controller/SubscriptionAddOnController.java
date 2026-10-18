package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.PurchaseAddOnRequest;
import com.company.bsmsvc.api.dto.response.AddOnPurchaseResponse;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionAddOnResponse;
import com.company.bsmsvc.api.mapper.SubscriptionAddOnApiMapper;
import com.company.bsmsvc.application.service.SubscriptionAddOnService;
import com.company.bsmsvc.domain.model.AddOnPurchaseResult;
import com.company.bsmsvc.domain.model.PurchaseAddOnCommand;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for PPM add-on billing (BSM C4).
 *
 * <p>Allows a subscription to purchase, list, and remove PPM add-ons.
 * BSM is the billing authority; PPM remains the catalog authority.
 *
 * <p>Base path: {@code /api/v1/bsm/subscriptions/{subscriptionId}/add-ons}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bsm/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription Add-Ons", description = "Purchase, list, and remove PPM add-ons attached to a subscription")
public class SubscriptionAddOnController {

    private final SubscriptionAddOnService subscriptionAddOnService;
    private final SubscriptionAddOnApiMapper mapper;

    // ── POST /…/{subscriptionId}/add-ons ─────────────────────────────────────

    @RequirePermission("billing.write")
    @PostMapping("/{subscriptionId}/add-ons")
    @Operation(summary = "Purchase a PPM add-on",
        description = "Resolves the current price from PPM, locks it permanently on the subscription, "
            + "generates an invoice, and returns a Stripe/Razorpay checkout URL.")
    public ResponseEntity<ApiResponse<AddOnPurchaseResponse>> purchaseAddOn(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody PurchaseAddOnRequest request) {
        log.debug("→ POST /api/v1/bsm/subscriptions/{}/add-ons ppmAddOnId={}",
            subscriptionId, request.ppmAddOnId());
        PurchaseAddOnCommand command = new PurchaseAddOnCommand(
            request.tenantId(), request.ppmAddOnId(), request.region(), request.cycle(),
            request.successUrl(), request.cancelUrl(), request.performedBy());
        AddOnPurchaseResult result = subscriptionAddOnService.purchaseAddOn(subscriptionId, command);
        AddOnPurchaseResponse response = mapper.toResponse(result);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Add-on purchased.", response));
    }

    // ── GET /…/{subscriptionId}/add-ons ──────────────────────────────────────

    @RequirePermission("billing.read")
    @GetMapping("/{subscriptionId}/add-ons")
    @Operation(summary = "List active add-ons",
        description = "Returns all currently active PPM add-ons attached to the subscription.")
    public ResponseEntity<ApiResponse<List<SubscriptionAddOnResponse>>> listAddOns(
            @PathVariable UUID subscriptionId,
            @RequestParam UUID tenantId) {
        log.debug("→ GET /api/v1/bsm/subscriptions/{}/add-ons tenantId={}", subscriptionId, tenantId);
        List<SubscriptionAddOnResponse> response = subscriptionAddOnService.listAddOns(subscriptionId, tenantId)
            .stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok("Add-ons retrieved.", response));
    }

    // ── DELETE /…/{subscriptionId}/add-ons/{ppmAddOnId} ──────────────────────

    @RequirePermission("billing.write")
    @DeleteMapping("/{subscriptionId}/add-ons/{ppmAddOnId}")
    @Operation(summary = "Remove an active add-on",
        description = "Soft-removes the add-on from the subscription. It will not appear in future "
            + "renewal invoices. No refund or proration is applied. The record is retained for auditing.")
    public ResponseEntity<Void> removeAddOn(
            @PathVariable UUID subscriptionId,
            @PathVariable UUID ppmAddOnId,
            @RequestParam UUID tenantId,
            @RequestParam(required = false, defaultValue = "system") String performedBy) {
        log.debug("→ DELETE /api/v1/bsm/subscriptions/{}/add-ons/{} tenantId={}",
            subscriptionId, ppmAddOnId, tenantId);
        subscriptionAddOnService.removeAddOn(subscriptionId, ppmAddOnId, tenantId, performedBy);
        return ResponseEntity.noContent().build();
    }
}
