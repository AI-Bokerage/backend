package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreatePlanPriceRequest;
import com.company.ppmsvc.api.dto.request.UpdatePlanPriceRequest;
import com.company.ppmsvc.api.dto.response.PlanPriceResponse;
import com.company.ppmsvc.api.mapper.PlanPriceApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.usecase.PlanPriceApplicationService;
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
 * REST API for the PPM Pricing Catalog.
 *
 * <p>Manages region-aware, cycle-specific price entries for subscription plans.
 * The pricing identity key is {@code (planId, region, currency, cycle, effectiveFrom)}.
 * All endpoints require {@code ppm.access} permission enforced by
 * {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 *
 * <p>Base path: {@code /api/v1/ppm/prices}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/prices")
@RequiredArgsConstructor
@Tag(name = "Pricing Catalog",
    description = "Plan pricing — create, update, retrieve, list, and soft-delete price entries.")
public class PlanPriceController {

    private final PlanPriceApplicationService planPriceService;
    private final PlanPriceApiMapper          apiMapper;

    // ── POST /api/v1/ppm/prices ───────────────────────────────────────────────

    @Operation(summary = "Create a new plan price",
        description = "Creates a region-aware, cycle-specific price entry for a plan. "
            + "The pricing identity (planId, region, currency, cycle, effectiveFrom) must be unique "
            + "across all active (non-deleted) price rows. "
            + "Currency and region are normalised to uppercase. "
            + "taxInclusive defaults to false when omitted. "
            + "effectiveFrom may be a future date for scheduled price changes.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Price created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND — the referenced plan does not exist"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PLAN_PRICE_ALREADY_EXISTS — a price for this key already exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation (missing required field or invalid amount)")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PlanPriceResponse>> create(
            @Valid @RequestBody CreatePlanPriceRequest request) {
        log.debug("→ POST /api/v1/ppm/prices");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        PlanPrice saved = planPriceService.createPrice(actorId, request.planId(), request.cycle(),
            request.currency(), request.region(), request.amount(), request.taxInclusive(),
            request.effectiveFrom());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Plan price created.", apiMapper.toResponse(saved)));
    }

    // ── PATCH /api/v1/ppm/prices/{id} ────────────────────────────────────────

    @Operation(summary = "Partially update a plan price",
        description = "Updates one or more mutable fields of an existing price entry. "
            + "Null fields are left unchanged (PATCH semantics). "
            + "The pricing identity fields (planId, cycle, currency, region, effectiveFrom) "
            + "are immutable and cannot be changed via this endpoint.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Price updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_PRICE_NOT_FOUND — price not found or has been deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "VALIDATION_ERROR — amount is zero or negative")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanPriceResponse>> update(
            @Parameter(description = "Price UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdatePlanPriceRequest request) {
        log.debug("→ PATCH /api/v1/ppm/prices id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        PlanPrice saved = planPriceService.updatePrice(actorId, id, request.amount(),
            request.taxInclusive(), request.active());
        return ResponseEntity.ok(
            ApiResponse.ok("Plan price updated.", apiMapper.toResponse(saved)));
    }

    // ── GET /api/v1/ppm/prices/{id} ───────────────────────────────────────────

    @Operation(summary = "Get a plan price by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Price found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_PRICE_NOT_FOUND — price not found or has been deleted")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanPriceResponse>> getById(
            @Parameter(description = "Price UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/prices id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan price retrieved.",
                apiMapper.toResponse(planPriceService.getPrice(id))));
    }

    // ── GET /api/v1/ppm/prices ────────────────────────────────────────────────

    @Operation(summary = "List plan prices",
        description = "Returns all non-deleted price entries matching the given criteria. "
            + "All query parameters are optional and may be combined. "
            + "cycle accepts wire values: monthly, annual.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Price list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid query parameter value (e.g. unknown cycle)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanPriceResponse>>> list(
            @Parameter(description = "Filter by plan UUID; omit for all plans")
            @RequestParam(required = false) UUID planId,
            @Parameter(description = "Filter by region (e.g. INDIA, US, EU); omit for all regions")
            @RequestParam(required = false) String region,
            @Parameter(description = "Filter by currency (ISO 4217, e.g. INR, USD); omit for all currencies")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Filter by billing cycle (monthly / annual); omit for both cycles")
            @RequestParam(required = false) BillingCycle cycle,
            @Parameter(description = "Filter by active flag; omit for all prices")
            @RequestParam(required = false) Boolean active) {
        log.debug("→ GET /api/v1/ppm/prices planId={} region={} currency={} cycle={}", planId, region, currency, cycle);
        return ResponseEntity.ok(
            ApiResponse.ok("Plan prices retrieved.",
                apiMapper.toResponseList(
                    planPriceService.listPrices(planId, region, currency, cycle, active))));
    }

    // ── DELETE /api/v1/ppm/prices/{id} ───────────────────────────────────────

    @Operation(summary = "Soft-delete a plan price",
        description = "Marks the price entry as deleted. "
            + "The record is retained in the database but hidden from all catalog queries. "
            + "The pricing key becomes available for reuse once the price is deleted.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Price deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing ppm.access permission"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLAN_PRICE_NOT_FOUND — price not found or already deleted")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Price UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/prices id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        planPriceService.deletePrice(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
