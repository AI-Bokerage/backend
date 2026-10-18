package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.AssignAddOnsRequest;
import com.company.ppmsvc.api.dto.response.PlanAddOnResponse;
import com.company.ppmsvc.api.mapper.PlanAddOnApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.planaddon.usecase.PlanAddOnApplicationService;
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
 * REST API for the Plan ↔ Add-On Assignment feature (PPM-11).
 *
 * <p>Manages the availability of purchasable add-ons on subscription plans.
 * This is a relationship layer only — no pricing logic is present here.
 *
 * <p>Base path: {@code /api/v1/ppm/plans/{planId}/add-ons}
 *
 * <p>All endpoints require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/plans")
@RequiredArgsConstructor
@Tag(name = "Plan Add-On Assignment", description = "Assign, list, replace, and remove purchasable add-ons on a subscription plan.")
public class PlanAddOnController {

    private final PlanAddOnApplicationService planAddOnService;
    private final PlanAddOnApiMapper          planAddOnApiMapper;

    // ── POST /{planId}/add-ons ────────────────────────────────────────────────

    @Operation(summary = "Assign add-ons to a plan",
        description = "Assigns one or more add-ons to a plan. "
            + "Duplicate IDs in the request are collapsed automatically (Set semantics). "
            + "Throws 409 if any add-on is already assigned to this plan.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Add-ons assigned successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan or add-on not found (PLAN_NOT_FOUND / ADD_ON_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "One of the add-ons is already assigned (PLAN_ADD_ON_ALREADY_ASSIGNED)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping("/{planId}/add-ons")
    public ResponseEntity<ApiResponse<Void>> assign(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId,
            @Valid @RequestBody AssignAddOnsRequest request) {
        log.debug("→ POST /api/v1/ppm/plans/{planId}/add-ons planId={}", planId);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        planAddOnService.assignAddOns(actorId, planId, request.addOnIds());
        return ResponseEntity.ok(ApiResponse.ok("Add-ons assigned.", null));
    }

    // ── GET /{planId}/add-ons ─────────────────────────────────────────────────

    @Operation(summary = "List add-ons assigned to a plan",
        description = "Returns all add-ons currently assigned to the plan, in assignment order.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Add-on list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found (PLAN_NOT_FOUND)")
    })
    @GetMapping("/{planId}/add-ons")
    public ResponseEntity<ApiResponse<List<PlanAddOnResponse>>> list(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId) {
        log.debug("→ GET /api/v1/ppm/plans/{planId}/add-ons planId={}", planId);
        return ResponseEntity.ok(
            ApiResponse.ok("Add-ons retrieved.", planAddOnApiMapper.toResponseList(planAddOnService.getPlanAddOns(planId))));
    }

    // ── PUT /{planId}/add-ons ─────────────────────────────────────────────────

    @Operation(summary = "Replace all add-ons on a plan",
        description = "Atomically replaces the complete set of add-ons assigned to a plan. "
            + "All existing assignments are removed and the supplied set is inserted in a single transaction. "
            + "All add-on IDs are validated before any deletion (abort-before-delete pattern).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Add-on set replaced successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan or add-on not found (PLAN_NOT_FOUND / ADD_ON_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PutMapping("/{planId}/add-ons")
    public ResponseEntity<ApiResponse<Void>> replace(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId,
            @Valid @RequestBody AssignAddOnsRequest request) {
        log.debug("→ PUT /api/v1/ppm/plans/{planId}/add-ons planId={}", planId);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        planAddOnService.replaceAddOns(actorId, planId, request.addOnIds());
        return ResponseEntity.ok(ApiResponse.ok("Add-ons replaced.", null));
    }

    // ── DELETE /{planId}/add-ons/{addOnId} ────────────────────────────────────

    @Operation(summary = "Remove an add-on from a plan",
        description = "Removes the assignment of an add-on from a plan. "
            + "The add-on itself is not deleted — only the assignment row is removed.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Add-on removed from plan"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found or mapping not found (PLAN_NOT_FOUND / PLAN_ADD_ON_MAPPING_NOT_FOUND)")
    })
    @DeleteMapping("/{planId}/add-ons/{addOnId}")
    public ResponseEntity<Void> remove(
            @Parameter(description = "Plan UUID", required = true)   @PathVariable UUID planId,
            @Parameter(description = "Add-on UUID", required = true) @PathVariable UUID addOnId) {
        log.debug("→ DELETE /api/v1/ppm/plans/{planId}/add-ons/{addOnId} planId={} addOnId={}", planId, addOnId);
        planAddOnService.removeAddOn(planId, addOnId);
        return ResponseEntity.noContent().build();
    }
}
