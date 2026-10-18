package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.ResolvePriceRequest;
import com.company.ppmsvc.api.dto.response.ResolvedPriceResponse;
import com.company.ppmsvc.api.mapper.PlanPriceApiMapper;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.usecase.PricingResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pricing Resolver Engine REST endpoint (PPM-09).
 *
 * <p>A single {@code POST /resolve} endpoint that returns the single currently
 * applicable price for a plan given a region, currency, and billing cycle.
 *
 * <p>Resolution semantics:
 * <ul>
 *   <li>Active price rows only</li>
 *   <li>Rows with {@code effectiveFrom} in the future are ignored</li>
 *   <li>When multiple applicable rows exist, the one with the latest
 *       {@code effectiveFrom} is selected</li>
 *   <li>Exact region and currency match — no fallback, no conversion</li>
 * </ul>
 *
 * <p>Base path: {@code /api/v1/ppm/prices}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/prices")
@RequiredArgsConstructor
@Tag(name = "Pricing Resolver", description = "Pricing Resolver Engine — resolve the applicable price for a plan.")
public class PricingResolverController {

    private final PricingResolver    pricingResolver;
    private final PlanPriceApiMapper apiMapper;

    // ── POST /api/v1/ppm/prices/resolve ──────────────────────────────────────

    @Operation(
        summary = "Resolve the applicable price for a plan",
        description = """
            Returns the single currently applicable price for the requested plan,
            region, currency, and billing cycle.

            Resolution rules:
            - Only active price rows are considered.
            - Rows with `effectiveFrom` after today are ignored.
            - When multiple active, past-effective rows exist, the one with the
              latest `effectiveFrom` is selected.
            - Region and currency are matched exactly (case-insensitive input,
              normalised to uppercase before lookup).
            - No currency conversion is performed.
            - No regional fallback is performed.

            Returns HTTP 404 when the plan does not exist or when no applicable
            price matches the requested criteria.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Price resolved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Plan not found (PLAN_NOT_FOUND) or no applicable price exists (PLAN_PRICE_NOT_RESOLVED)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422",
            description = "Request body failed bean validation")
    })
    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<ResolvedPriceResponse>> resolve(
            @Valid @RequestBody ResolvePriceRequest request) {
        log.debug("→ POST /api/v1/ppm/prices/resolve planId={} region={} currency={} cycle={}",
            request.planId(), request.region(), request.currency(), request.cycle());
        PlanPrice resolved = pricingResolver.resolvePrice(
            request.planId(), request.region(), request.currency(), request.cycle());
        return ResponseEntity.ok(
            ApiResponse.ok("Price resolved.", apiMapper.toResolvedPriceResponse(resolved)));
    }
}
