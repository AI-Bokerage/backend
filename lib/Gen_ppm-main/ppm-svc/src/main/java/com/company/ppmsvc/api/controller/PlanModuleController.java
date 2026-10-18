package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.AssignModulesRequest;
import com.company.ppmsvc.api.dto.response.PlanModuleResponse;
import com.company.ppmsvc.api.mapper.PlanModuleApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.planmodule.usecase.PlanModuleApplicationService;
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
 * REST API for the Plan ↔ Module Mapping feature (PPM-03).
 *
 * <p>Manages assignment of platform capability modules to subscription plans.
 * This is a relationship layer only — no entitlements, pricing, or tenant
 * provisioning logic is present here.
 *
 * <p>Base path: {@code /api/v1/ppm/plans/{planId}/modules}
 *
 * <p>All endpoints require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/plans")
@RequiredArgsConstructor
@Tag(name = "Plan Module Mapping", description = "Assign, list, replace, and remove capability modules on a subscription plan.")
public class PlanModuleController {

    private final PlanModuleApplicationService planModuleService;
    private final PlanModuleApiMapper          planModuleApiMapper;

    // ── POST /{planId}/modules ────────────────────────────────────────────────

    @Operation(summary = "Assign modules to a plan",
        description = "Assigns one or more capability modules to a plan. "
            + "Duplicate IDs in the request are collapsed automatically. "
            + "Returns the full list of newly assigned modules.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Modules assigned successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan or module not found (PLAN_NOT_FOUND / MODULE_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "One of the modules is already assigned (MODULE_ALREADY_ASSIGNED_TO_PLAN)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping("/{planId}/modules")
    public ResponseEntity<ApiResponse<List<PlanModuleResponse>>> assign(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId,
            @Valid @RequestBody AssignModulesRequest request) {
        log.debug("→ POST /api/v1/ppm/plans/{planId}/modules planId={}", planId);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var modules = planModuleService.assignModules(actorId, planId, request.moduleIds());
        return ResponseEntity.ok(
            ApiResponse.ok("Modules assigned.", modules.stream().map(planModuleApiMapper::toResponse).toList()));
    }

    // ── GET /{planId}/modules ─────────────────────────────────────────────────

    @Operation(summary = "List modules assigned to a plan",
        description = "Returns all capability modules currently assigned to the plan, in assignment order.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found (PLAN_NOT_FOUND)")
    })
    @GetMapping("/{planId}/modules")
    public ResponseEntity<ApiResponse<List<PlanModuleResponse>>> list(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId) {
        log.debug("→ GET /api/v1/ppm/plans/{planId}/modules planId={}", planId);
        var modules = planModuleService.getModules(planId);
        return ResponseEntity.ok(
            ApiResponse.ok("Modules retrieved.", modules.stream().map(planModuleApiMapper::toResponse).toList()));
    }

    // ── PUT /{planId}/modules ─────────────────────────────────────────────────

    @Operation(summary = "Replace all modules on a plan",
        description = "Atomically replaces the complete set of modules assigned to a plan. "
            + "All existing assignments are removed and the supplied set is inserted in a single transaction. "
            + "Sending an empty set clears all assignments.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module set replaced successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan or module not found (PLAN_NOT_FOUND / MODULE_NOT_FOUND)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PutMapping("/{planId}/modules")
    public ResponseEntity<ApiResponse<List<PlanModuleResponse>>> replace(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId,
            @Valid @RequestBody AssignModulesRequest request) {
        log.debug("→ PUT /api/v1/ppm/plans/{planId}/modules planId={}", planId);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var modules = planModuleService.replaceModules(actorId, planId, request.moduleIds());
        return ResponseEntity.ok(
            ApiResponse.ok("Modules replaced.", modules.stream().map(planModuleApiMapper::toResponse).toList()));
    }

    // ── DELETE /{planId}/modules/{moduleId} ───────────────────────────────────

    @Operation(summary = "Remove a module from a plan",
        description = "Removes the mapping between a plan and a module. "
            + "The module itself is not deleted — only the assignment row is removed.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Module removed from plan"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found or mapping not found (PLAN_NOT_FOUND / PLAN_MODULE_MAPPING_NOT_FOUND)")
    })
    @DeleteMapping("/{planId}/modules/{moduleId}")
    public ResponseEntity<Void> remove(
            @Parameter(description = "Plan UUID", required = true)   @PathVariable UUID planId,
            @Parameter(description = "Module UUID", required = true) @PathVariable UUID moduleId) {
        log.debug("→ DELETE /api/v1/ppm/plans/{planId}/modules/{moduleId} planId={} moduleId={}", planId, moduleId);
        planModuleService.removeModule(planId, moduleId);
        return ResponseEntity.noContent().build();
    }
}
