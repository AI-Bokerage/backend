package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreatePlanVersionRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanVersionRequest;
import com.company.ppmsvc.api.dto.response.PlanVersionResponse;
import com.company.ppmsvc.api.mapper.PlanVersionApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.model.PlanVersionLimitsResponse;
import com.company.ppmsvc.plan.model.PlanVersionMetaResponse;
import com.company.ppmsvc.plan.usecase.PlanVersionApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the PPM Plan Versioning Catalog.
 *
 * <p>Manages the lifecycle of plan versions — creation, update, retrieval, and
 * soft-delete. Each plan may have multiple versions with non-overlapping effective
 * date ranges. When a new version is created, the previous open-ended version is
 * automatically closed (BR-6 auto-close logic lives entirely in the service layer).
 *
 * <p>All endpoints require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 *
 * <p>Base path: {@code /api/v1/ppm}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm")
@RequiredArgsConstructor
@Tag(name = "Plan Versioning",
    description = "Plan version catalog — create, update, retrieve, list, and soft-delete plan versions.")
public class PlanVersionController {

    private final PlanVersionApplicationService planVersionService;
    private final PlanVersionApiMapper          apiMapper;

    // ── POST /api/v1/ppm/versions ─────────────────────────────────────────────

    @Operation(summary = "Create a new plan version",
        description = "Creates a new version entry for an existing plan. "
            + "The version identity (planId, versionNo) must be unique per plan among active rows. "
            + "effectiveFrom must be unique per plan and must not fall inside an existing "
            + "closed version's date range. "
            + "If a previous open-ended version exists, it is automatically closed by setting "
            + "its effectiveTo = newEffectiveFrom.minusDays(1). "
            + "active defaults to true when omitted. "
            + "effectiveFrom may be a future date for scheduled version activations.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Version created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND — the referenced plan does not exist"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PLAN_VERSION_ALREADY_EXISTS — versionNo or effectiveFrom already exists for this plan, "
            + "or PLAN_VERSION_DATE_CONFLICT — effectiveFrom falls inside an existing version's date range"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed bean validation (missing required field)")
    })
    @PostMapping("/versions")
    public ResponseEntity<ApiResponse<PlanVersionResponse>> create(
            @Valid @RequestBody CreatePlanVersionRequest request) {
        log.debug("→ POST /api/v1/ppm/versions");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        PlanVersion saved = planVersionService.createVersion(actorId, request.planId(), request.versionNo(),
            request.effectiveFrom(), request.active(), request.maxInternalUsers(), request.maxClientUsers(),
            request.maxActiveProjects(), request.storageQuotaBytes(), request.customDomainEnabled(),
            request.ssoEnabled(), request.prioritySupport());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Plan version created.", apiMapper.toResponse(saved)));
    }

    // ── PATCH /api/v1/ppm/versions/{id} ──────────────────────────────────────

    @Operation(summary = "Partially update a plan version",
        description = "Updates one or more mutable fields of an existing plan version. "
            + "Null fields are left unchanged (PATCH semantics). "
            + "The identity fields (planId, versionNo, effectiveFrom) are immutable "
            + "and cannot be changed via this endpoint (BR-4). "
            + "effectiveTo must not be before effectiveFrom.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Version updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_VERSION_NOT_FOUND — version not found or has been deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "VALIDATION_ERROR — effectiveTo is before effectiveFrom")
    })
    @PatchMapping("/versions/{id}")
    public ResponseEntity<ApiResponse<PlanVersionResponse>> update(
            @Parameter(description = "Version UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdatePlanVersionRequest request) {
        log.debug("→ PATCH /api/v1/ppm/versions id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        PlanVersion saved = planVersionService.updateVersion(actorId, id, request.effectiveTo(), request.active(),
            request.maxInternalUsers(), request.maxClientUsers(), request.maxActiveProjects(),
            request.storageQuotaBytes(), request.customDomainEnabled(), request.ssoEnabled(),
            request.prioritySupport());
        return ResponseEntity.ok(
            ApiResponse.ok("Plan version updated.", apiMapper.toResponse(saved)));
    }

    // ── GET /api/v1/ppm/versions/{id} ────────────────────────────────────────

    @Operation(summary = "Get a plan version by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Version found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_VERSION_NOT_FOUND — version not found or has been deleted")
    })
    @GetMapping("/versions/{id}")
    public ResponseEntity<ApiResponse<PlanVersionResponse>> getById(
            @Parameter(description = "Version UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/versions id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan version retrieved.",
                apiMapper.toResponse(planVersionService.getVersion(id))));
    }

    // ── GET /api/v1/ppm/plans/{planId}/versions ───────────────────────────────

    @Operation(summary = "List versions for a plan",
        description = "Returns all non-deleted version entries for the given plan, "
            + "ordered by versionNo descending (most recent first).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Version list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission")
    })
    @GetMapping("/plans/{planId}/versions")
    public ResponseEntity<ApiResponse<List<PlanVersionResponse>>> listByPlan(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId) {
        log.debug("→ GET /api/v1/ppm/plans/{planId}/versions planId={}", planId);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan versions retrieved.",
                apiMapper.toResponseList(planVersionService.listVersions(planId, null))));
    }

    // ── GET /api/v1/ppm/plans/{planId}/versions/latest ────────────────────────

    @Operation(summary = "Get the latest version for a plan",
        description = "Returns the version with the highest versionNo for the given plan.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Latest version returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND — plan does not exist, "
            + "or PLAN_VERSION_NOT_FOUND — plan exists but has no active versions")
    })
    @GetMapping("/plans/{planId}/versions/latest")
    public ResponseEntity<ApiResponse<PlanVersionResponse>> getLatest(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID planId) {
        log.debug("→ GET /api/v1/ppm/plans/{planId}/versions/latest planId={}", planId);
        return ResponseEntity.ok(
            ApiResponse.ok("Latest plan version retrieved.",
                apiMapper.toResponse(planVersionService.getLatestVersion(planId))));
    }

    // ── GET /api/v1/ppm/plan-versions/{id}/meta ──────────────────────────────

    @Operation(summary = "Get plan version metadata (public)",
        description = "Returns lightweight metadata for a plan version joined with its parent plan. "
            + "Public — no Authorization header required. "
            + "Used by BSM for event publisher plan-code resolution and version drift detection.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Metadata returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_VERSION_NOT_FOUND or PLAN_NOT_FOUND")
    })
    @GetMapping("/plan-versions/{id}/meta")
    public ResponseEntity<ApiResponse<PlanVersionMetaResponse>> getMeta(
            @Parameter(description = "Version UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/plan-versions/{}/meta", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan version metadata retrieved.",
                planVersionService.getPlanVersionMeta(id)));
    }

    // ── GET /api/v1/ppm/plan-versions/{id}/limits ─────────────────────────────

    @Operation(summary = "Get plan version limits (public)",
        description = "Returns the capacity limits for a plan version. "
            + "Public — no Authorization header required. "
            + "Used by BSM executeDowngradePreflight to compare tenant usage against target limits.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Limits returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_VERSION_NOT_FOUND")
    })
    @GetMapping("/plan-versions/{id}/limits")
    public ResponseEntity<ApiResponse<PlanVersionLimitsResponse>> getLimits(
            @Parameter(description = "Version UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/plan-versions/{}/limits", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan version limits retrieved.",
                planVersionService.getPlanVersionLimits(id)));
    }

    // ── DELETE /api/v1/ppm/versions/{id} ─────────────────────────────────────

    @Operation(summary = "Soft-delete a plan version",
        description = "Marks the version as deleted. "
            + "The record is retained in the database but hidden from all catalog queries. "
            + "The version identity becomes available for reuse once deleted.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Version deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_VERSION_NOT_FOUND — version not found or already deleted")
    })
    @DeleteMapping("/versions/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Version UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/versions id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        planVersionService.deleteVersion(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
