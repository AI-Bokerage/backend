package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.AssignEntitlementsRequest;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.planentitlement.model.ResolvedEntitlementResponse;
import com.company.ppmsvc.planentitlement.usecase.EntitlementResolver;
import com.company.ppmsvc.planentitlement.usecase.PlanEntitlementApplicationService;
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
 * REST API for managing Plan ↔ Entitlement assignments.
 *
 * <p>Sub-resource of Plan: all paths are under
 * {@code /api/v1/ppm/plans/{planId}/entitlements}.
 *
 * <p>All endpoints require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/plans")
@RequiredArgsConstructor
@Tag(name = "Plan Entitlements",
    description = "Assign, replace, query, and remove entitlements on a subscription plan.")
public class PlanEntitlementController {

    private final PlanEntitlementApplicationService planEntitlementService;
    private final EntitlementResolver               entitlementResolver;

    // ── POST /api/v1/ppm/plans/{planId}/entitlements ──────────────────────────

    @Operation(summary = "Assign entitlements to a plan",
        description = "Appends one or more entitlement assignments to the plan. "
            + "An entitlement already assigned to the plan (same planId + entitlementId) "
            + "is rejected with 409.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entitlements assigned; returns the updated full list"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request body missing or invalid"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND or ENTITLEMENT_NOT_FOUND"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PLAN_ENTITLEMENT_ALREADY_ASSIGNED — entitlement already present on plan")
    })
    @PostMapping("/{planId}/entitlements")
    public ResponseEntity<ApiResponse<List<ResolvedEntitlementResponse>>> assign(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId,
            @Valid @RequestBody AssignEntitlementsRequest request) {
        log.debug("→ POST /api/v1/ppm/plans/{planId}/entitlements planId={}", planId);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(
            ApiResponse.ok("Entitlements assigned.",
                planEntitlementService.assignEntitlements(actorId, planId, request.entitlementIds())));
    }

    // ── PUT /api/v1/ppm/plans/{planId}/entitlements ───────────────────────────

    @Operation(summary = "Replace all entitlements on a plan",
        description = "Atomically replaces the full set of entitlements assigned to a plan. "
            + "All existing assignments are deleted and the supplied list is inserted in one "
            + "transaction. Validation of every entitlement ID in the request occurs before "
            + "any deletions are performed — a bad ID leaves original assignments intact.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entitlements replaced; returns the new full list"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request body missing or invalid"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND or ENTITLEMENT_NOT_FOUND")
    })
    @PutMapping("/{planId}/entitlements")
    public ResponseEntity<ApiResponse<List<ResolvedEntitlementResponse>>> replace(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId,
            @Valid @RequestBody AssignEntitlementsRequest request) {
        log.debug("→ PUT /api/v1/ppm/plans/{planId}/entitlements planId={}", planId);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(
            ApiResponse.ok("Entitlements replaced.",
                planEntitlementService.replaceEntitlements(actorId, planId, request.entitlementIds())));
    }

    // ── GET /api/v1/ppm/plans/{planId}/entitlements/resolved ─────────────────
    // Note: declared before the /{planId}/entitlements mapping to ensure Spring
    // matches the literal segment "resolved" before the empty suffix.

    @Operation(summary = "Resolve the effective entitlement set for a plan",
        description = "Returns the full set of entitlements assigned to the plan as resolved "
            + "code → value pairs. Uses a single batch IN-query — no N+1.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resolved entitlement set returned; empty list if none assigned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND")
    })
    @GetMapping("/{planId}/entitlements/resolved")
    public ResponseEntity<ApiResponse<List<ResolvedEntitlementResponse>>> resolved(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId) {
        log.debug("→ GET /api/v1/ppm/plans/{planId}/entitlements/resolved planId={}", planId);
        return ResponseEntity.ok(
            ApiResponse.ok("Entitlements resolved.",
                entitlementResolver.resolveEntitlements(planId)));
    }

    // ── GET /api/v1/ppm/plans/{planId}/entitlements ───────────────────────────

    @Operation(summary = "List entitlements assigned to a plan",
        description = "Returns all entitlements currently assigned to the plan as resolved "
            + "code → value pairs.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entitlement list returned; empty list if none assigned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND")
    })
    @GetMapping("/{planId}/entitlements")
    public ResponseEntity<ApiResponse<List<ResolvedEntitlementResponse>>> list(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId) {
        log.debug("→ GET /api/v1/ppm/plans/{planId}/entitlements planId={}", planId);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan entitlements retrieved.",
                planEntitlementService.getPlanEntitlements(planId)));
    }

    // ── DELETE /api/v1/ppm/plans/{planId}/entitlements/{entitlementId} ────────

    @Operation(summary = "Remove a single entitlement from a plan",
        description = "Deletes the mapping row between the plan and the given entitlement. "
            + "The entitlement catalog entry itself is not affected.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Entitlement removed from plan"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND or PLAN_ENTITLEMENT_MAPPING_NOT_FOUND")
    })
    @DeleteMapping("/{planId}/entitlements/{entitlementId}")
    public ResponseEntity<Void> remove(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId,
            @Parameter(description = "Entitlement UUID", required = true) @PathVariable UUID entitlementId) {
        log.debug("→ DELETE /api/v1/ppm/plans/{planId}/entitlements/{entitlementId} planId={} entitlementId={}", planId, entitlementId);
        planEntitlementService.removeEntitlement(planId, entitlementId);
        return ResponseEntity.noContent().build();
    }
}
