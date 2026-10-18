package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.ApplyPlanChangeRequest;
import com.company.bsmsvc.api.dto.request.PreviewPlanChangeRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.dto.response.PlanChangeApplyResponse;
import com.company.bsmsvc.api.dto.response.PlanChangePreviewResponse;
import com.company.bsmsvc.application.service.SubscriptionChangeService;
import com.company.bsmsvc.domain.model.ApplyPlanChangeCommand;
import com.company.bsmsvc.domain.model.PlanChangeApplyResult;
import com.company.bsmsvc.domain.model.PlanChangePreviewResult;
import com.company.bsmsvc.domain.model.PreviewPlanChangeCommand;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/bsm/subscriptions")
@RequiredArgsConstructor
@Tag(name = "PPM Plan Change", description = "PPM-backed subscription plan change (upgrade / downgrade)")
public class PpmPlanChangeController {

    private final SubscriptionChangeService subscriptionChangeService;

    @RequirePermission("billing.read")
    @PostMapping("/{subscriptionId}/ppm-plan-change/preview")
    @Operation(summary = "Preview a PPM plan change",
        description = "Pure read-only. Returns proration amounts and change type without making any writes.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
        description = "Preview calculated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
        description = "Validation error or subscription not PPM-backed",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
        description = "PPM service unavailable",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ApiResponse<PlanChangePreviewResponse>> previewChange(
        @PathVariable UUID subscriptionId,
        @Valid @RequestBody PreviewPlanChangeRequest request
    ) {
        log.debug("→ POST /api/v1/bsm/subscriptions/{}/ppm-plan-change/preview tenantId={}",
            subscriptionId, request.tenantId());
        PreviewPlanChangeCommand command = new PreviewPlanChangeCommand(
            request.tenantId(), request.targetPpmPlanId(), request.region(), request.cycle(), request.promoCode());
        PlanChangePreviewResult result = subscriptionChangeService.previewChange(subscriptionId, command);
        PlanChangePreviewResponse response = new PlanChangePreviewResponse(
            result.subscriptionId(), result.currentPpmPlanId(), result.currentPpmResolvedPriceMinor(),
            result.targetPpmPlanId(), result.targetPpmPriceId(), result.targetPpmPlanVersionId(),
            result.targetPpmResolvedPriceMinor(), result.prorationCreditMinor(), result.prorationChargeMinor(),
            result.prorationNetMinor(), result.currency(), result.changeType(), result.promoDiscountMinor(),
            result.discountedNetAmountMinor());
        return ResponseEntity.ok(ApiResponse.ok("Plan change preview calculated", response));
    }

    @RequirePermission("billing.write")
    @PostMapping("/{subscriptionId}/ppm-plan-change/apply")
    @Operation(summary = "Apply a PPM plan change",
        description = "Updates subscription PPM fields atomically. "
            + "For upgrades a proration invoice is created and a checkout URL is returned. "
            + "For downgrades the new lower price takes effect at the next renewal; no invoice is created.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
        description = "Plan change applied successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
        description = "Validation error, subscription not ACTIVE, or same-plan no-op",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
        description = "PPM service unavailable",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ApiResponse<PlanChangeApplyResponse>> applyChange(
        @PathVariable UUID subscriptionId,
        @Valid @RequestBody ApplyPlanChangeRequest request
    ) {
        log.debug("→ POST /api/v1/bsm/subscriptions/{}/ppm-plan-change/apply tenantId={} targetPpmPlanId={}",
            subscriptionId, request.tenantId(), request.targetPpmPlanId());
        ApplyPlanChangeCommand command = new ApplyPlanChangeCommand(
            request.tenantId(), request.targetPpmPlanId(), request.region(), request.cycle(),
            request.reason(), request.performedBy(), request.successUrl(), request.cancelUrl(),
            request.promoCode());
        PlanChangeApplyResult result = subscriptionChangeService.applyChange(subscriptionId, command);
        PlanChangeApplyResponse response = new PlanChangeApplyResponse(
            result.subscriptionId(), result.invoiceId(), result.checkoutUrl(), result.sessionId(),
            result.netAmountMinor(), result.currency(), result.changeType(), result.ppmPlanVersionId(),
            result.promoDiscountMinor(), result.discountedNetAmountMinor());
        return ResponseEntity.ok(ApiResponse.ok("Plan change applied successfully", response));
    }
}
