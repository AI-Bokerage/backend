package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.AssignPromoPlansRequest;
import com.company.ppmsvc.api.dto.response.PromoCodePlanResponse;
import com.company.ppmsvc.api.mapper.PromoCodePlanApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.promocodeplan.usecase.PromoCodePlanApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for Promo Code ↔ Plan Restrictions (PPM-07).
 *
 * <p>Manages optional plan-level restrictions on promo codes. When at least one
 * restriction row exists the promo code is limited to those plans; no rows means
 * the code applies to all plans.
 *
 * <p>Base path: {@code /api/v1/ppm/promo-codes/{promoCodeId}/plans}
 *
 * <p>All endpoints require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/promo-codes")
@RequiredArgsConstructor
@Tag(name = "Promo Code Plan Restrictions", description = "Assign, list, replace, and remove plan restrictions on a promo code.")
public class PromoCodePlanController {

    private final PromoCodePlanApplicationService promoCodePlanService;
    private final PromoCodePlanApiMapper          apiMapper;

    // ── POST /{promoCodeId}/plans ─────────────────────────────────────────────

    @Operation(summary = "Assign plans to a promo code",
        description = "Assigns one or more plans to a promo code, restricting it to those plans. "
            + "Duplicate IDs in the request are collapsed automatically. "
            + "Returns the newly created restriction mappings.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plans assigned successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Promo code or plan not found (PROMO_CODE_NOT_FOUND / PLAN_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "One of the plans is already restricted (PROMO_CODE_PLAN_ALREADY_ASSIGNED)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping("/{promoCodeId}/plans")
    public ResponseEntity<ApiResponse<List<PromoCodePlanResponse>>> assign(
            @Parameter(description = "Promo code UUID", required = true) @PathVariable UUID promoCodeId,
            @Valid @RequestBody AssignPromoPlansRequest request) {
        log.debug("→ POST /api/v1/ppm/promo-codes/{promoCodeId}/plans promoCodeId={}", promoCodeId);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(
            ApiResponse.ok("Plans assigned.",
                apiMapper.toResponseList(promoCodePlanService.assignPlans(actorId, promoCodeId, request.planIds()))));
    }

    // ── PUT /{promoCodeId}/plans ──────────────────────────────────────────────

    @Operation(summary = "Replace all plan restrictions on a promo code",
        description = "Atomically replaces the complete set of plan restrictions. "
            + "All plans are validated before any deletion occurs. "
            + "Sending an empty set is rejected (use DELETE per-plan to clear restrictions individually).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan restrictions replaced successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Promo code or plan not found (PROMO_CODE_NOT_FOUND / PLAN_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PutMapping("/{promoCodeId}/plans")
    public ResponseEntity<ApiResponse<List<PromoCodePlanResponse>>> replace(
            @Parameter(description = "Promo code UUID", required = true) @PathVariable UUID promoCodeId,
            @Valid @RequestBody AssignPromoPlansRequest request) {
        log.debug("→ PUT /api/v1/ppm/promo-codes/{promoCodeId}/plans promoCodeId={}", promoCodeId);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(
            ApiResponse.ok("Plans replaced.",
                apiMapper.toResponseList(promoCodePlanService.replacePlans(actorId, promoCodeId, request.planIds()))));
    }

    // ── GET /{promoCodeId}/plans ──────────────────────────────────────────────

    @Operation(summary = "List plan restrictions on a promo code",
        description = "Returns all plan restrictions for the promo code, in insertion order. "
            + "An empty list means the code is unrestricted (applies to all plans).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Restriction list returned")
    })
    @GetMapping("/{promoCodeId}/plans")
    public ResponseEntity<ApiResponse<List<PromoCodePlanResponse>>> list(
            @Parameter(description = "Promo code UUID", required = true) @PathVariable UUID promoCodeId) {
        log.debug("→ GET /api/v1/ppm/promo-codes/{promoCodeId}/plans promoCodeId={}", promoCodeId);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan restrictions retrieved.",
                apiMapper.toResponseList(promoCodePlanService.getRestrictedPlans(promoCodeId))));
    }

    // ── DELETE /{promoCodeId}/plans/{planId} ──────────────────────────────────

    @Operation(summary = "Remove a plan restriction from a promo code",
        description = "Removes the restriction mapping between a promo code and a plan. "
            + "The promo code and the plan themselves are not deleted.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Restriction removed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Mapping not found (PROMO_CODE_PLAN_MAPPING_NOT_FOUND)")
    })
    @DeleteMapping("/{promoCodeId}/plans/{planId}")
    public ResponseEntity<Void> remove(
            @Parameter(description = "Promo code UUID", required = true) @PathVariable UUID promoCodeId,
            @Parameter(description = "Plan UUID",       required = true) @PathVariable UUID planId) {
        log.debug("→ DELETE /api/v1/ppm/promo-codes/{promoCodeId}/plans/{planId} promoCodeId={} planId={}", promoCodeId, planId);
        promoCodePlanService.removePlan(promoCodeId, planId);
        return ResponseEntity.noContent().build();
    }
}
