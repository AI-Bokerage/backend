package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreatePlanRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanRequest;
import com.company.ppmsvc.api.dto.response.DefaultTrialResponse;
import com.company.ppmsvc.api.dto.response.PlanResponse;
import com.company.ppmsvc.api.mapper.PlanApiMapper;
import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.plan.model.PlanVisibility;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the PPM Plan Catalog.
 *
 * <p>Manages the platform-wide catalog of subscription plans. All endpoints
 * require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 *
 * <p>Base path: {@code /api/v1/ppm/plans}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/plans")
@RequiredArgsConstructor
@Tag(name = "Plan Catalog", description = "Subscription plan catalog — create, update, retrieve, and soft-delete plans.")
public class PlanController {

    private final PlanApplicationService planService;
    private final PlanApiMapper          planApiMapper;

    // ── POST /api/v1/ppm/plans ────────────────────────────────────────────────

    @Operation(summary = "Create a new plan",
        description = "Creates a new subscription plan. Both code and slug must be unique across all active (non-deleted) plans.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Plan created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A plan with the given code or slug already exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PlanResponse>> create(
            @Valid @RequestBody CreatePlanRequest request) {
        log.debug("→ POST /api/v1/ppm/plans");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var plan = planService.createPlan(actorId, request.code(), request.name(), request.tagline(),
            request.description(), request.visibility(), request.trialDays(), request.active(), request.tier());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Plan created.", planApiMapper.toResponse(plan)));
    }

    // ── PATCH /api/v1/ppm/plans/{id} ─────────────────────────────────────────

    @Operation(summary = "Partially update a plan",
        description = "Updates one or more fields of an existing plan. Null fields are left unchanged. "
            + "Both code and slug are immutable after creation.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found or has been deleted")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanResponse>> update(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdatePlanRequest request) {
        log.debug("→ PATCH /api/v1/ppm/plans id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var plan = planService.updatePlan(actorId, id, request.name(), request.tagline(), request.description(),
            request.visibility(), request.trialDays(), request.active(), request.tier());
        return ResponseEntity.ok(
            ApiResponse.ok("Plan updated.", planApiMapper.toResponse(plan)));
    }

    // ── GET /api/v1/ppm/plans/{id} ────────────────────────────────────────────

    @Operation(summary = "Get a plan by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found or has been deleted")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanResponse>> getById(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/plans id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan retrieved.", planApiMapper.toResponse(planService.getPlan(id))));
    }

    // ── GET /api/v1/ppm/plans/slug/{slug} ────────────────────────────────────

    @Operation(summary = "Get a plan by its slug",
        description = "Looks up a plan by its immutable URL slug (e.g. \"starter-plan\"). "
            + "Slugs are lowercase-only and immutable after creation.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active plan with the given slug")
    })
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<PlanResponse>> getBySlug(
            @Parameter(description = "Plan slug (e.g. \"starter-plan\")", required = true)
            @PathVariable String slug) {
        log.debug("→ GET /api/v1/ppm/plans/slug slug={}", slug);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan retrieved.", planApiMapper.toResponse(planService.getPlanBySlug(slug))));
    }

    // ── GET /api/v1/ppm/plans ─────────────────────────────────────────────────

    @Operation(summary = "List plans",
        description = "Returns all non-deleted plans ordered by code ascending. "
            + "Pass ?active=true/false to filter by active flag. "
            + "Pass ?visibility=public/private/legacy to filter by visibility. "
            + "Both filters may be combined.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan list returned")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanResponse>>> list(
            @Parameter(description = "Filter by active flag; omit for all plans")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filter by visibility (public / private / legacy); omit for all plans")
            @RequestParam(required = false) PlanVisibility visibility) {
        log.debug("→ GET /api/v1/ppm/plans active={} visibility={}", active, visibility);
        return ResponseEntity.ok(
            ApiResponse.ok("Plans retrieved.", planApiMapper.toResponseList(planService.listPlans(active, visibility))));
    }

    // ── GET /api/v1/ppm/plans/code/{code} ────────────────────────────────────

    @Operation(summary = "Get a plan by its code (public)",
        description = "Resolves a plan by its stable machine-readable code (e.g. \"starter\"). "
            + "Returns the plan UUID and latest active plan-version UUID so callers can store "
            + "canonical IDs without knowing internal UUIDs. No authentication required.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active plan with the given code, or plan has no active version")
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<DefaultTrialResponse>> getByCode(
            @Parameter(description = "Plan code (e.g. \"starter\")", required = true)
            @PathVariable String code) {
        log.debug("→ GET /api/v1/ppm/plans/code code={}", code);
        var result = planService.getPlanByCode(code);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan retrieved.", new DefaultTrialResponse(
                result.plan().getId(), result.plan().getCode(), result.activeVersion().getId())));
    }

    // ── GET /api/v1/ppm/plans/default-trial ──────────────────────────────────

    @Operation(summary = "Get the default trial plan (public)",
        description = "Returns the canonical trial plan and its latest active version. "
            + "Used by BSM TenantCreatedConsumer to provision new-tenant TRIALING subscriptions. "
            + "No authentication required.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Default trial plan found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active trial plan in catalog")
    })
    @GetMapping("/default-trial")
    public ResponseEntity<ApiResponse<DefaultTrialResponse>> getDefaultTrialPlan() {
        log.debug("→ GET /api/v1/ppm/plans/default-trial");
        var result = planService.getDefaultTrialPlan();
        return ResponseEntity.ok(
            ApiResponse.ok("Default trial plan retrieved.", new DefaultTrialResponse(
                result.plan().getId(), result.plan().getCode(), result.activeVersion().getId())));
    }

    // ── DELETE /api/v1/ppm/plans/{id} ─────────────────────────────────────────

    @Operation(summary = "Soft-delete a plan",
        description = "Marks the plan as deleted. The record is retained in the database but hidden from all catalog queries.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Plan deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found or already deleted")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Plan UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/plans id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        planService.deletePlan(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
