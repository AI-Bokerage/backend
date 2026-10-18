package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CustomerContextRequest;
import com.company.ppmsvc.api.dto.request.PriceQuoteRequest;
import com.company.ppmsvc.api.dto.response.PriceQuoteResponse;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.PriceQuote;
import com.company.ppmsvc.promotion.usecase.PromotionPricingService;
import com.company.ppmsvc.security.PpmAuthorizationService;
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
 * Promotion Pricing Engine REST endpoint.
 *
 * <p>A single {@code POST /} endpoint that resolves a plan's price and
 * applies a coupon's promotion, if any. Always returns HTTP 200 for
 * coupon/promotion outcomes — inspect {@code reason} in the response body.
 * Only a missing plan or price returns HTTP 404.
 *
 * <p>Base path: {@code /api/v1/ppm/quotes}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/quotes")
@RequiredArgsConstructor
@Tag(name = "Price Quotes", description = "Promotion Pricing Engine — resolve a plan's price with an optional coupon applied.")
public class PriceQuoteController {

    private final PromotionPricingService pricingService;
    private final PpmAuthorizationService authorizationService;

    @Operation(
        summary = "Resolve a price quote for a plan, optionally applying a coupon",
        description = """
            Resolves the applicable price for the given plan/region/currency/cycle,
            then applies the promotion behind `couponCode`, if supplied.

            Always returns HTTP 200. Inspect `reason` for the outcome:

            - `valid`                  — coupon applied; discountAmount/finalAmount reflect it.
            - `no_coupon`              — no couponCode supplied; finalAmount equals baseAmount.
            - `coupon_not_found`       — no active coupon with that code exists.
            - `coupon_inactive`        — the coupon has been deactivated.
            - `promotion_inactive`     — the promotion is inactive, draft, or missing.
            - `promotion_not_started`  — today is before the promotion's validFrom date.
            - `promotion_expired`      — today is after the promotion's validUntil date.
            - `plan_not_eligible`      — the promotion is restricted to other plans (requires `customerContext`).
            - `eligibility_violation`  — the customer doesn't match the promotion's eligibility rule (requires `customerContext`).
            - `usage_limit_per_user`   — the customer already reached the promotion's per-user cap (requires `customerContext`).

            Supplying `customerContext` (with `customerId`) evaluates the promotion's
            conditions in addition to status/validity — `conditionsSkipped=false`.
            Omitting it keeps the Phase-0 behaviour — `conditionsSkipped=true`.

            Returns HTTP 404 only when the plan or its price cannot be resolved
            (PLAN_NOT_FOUND / PLAN_PRICE_NOT_RESOLVED).
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Quote resolved — inspect `reason` in the response body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Plan or price could not be resolved")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PriceQuoteResponse>> quote(
            @Valid @RequestBody PriceQuoteRequest request) {
        log.debug("→ POST /api/v1/ppm/quotes planId={} couponCode={}", request.planId(), request.couponCode());
        authorizationService.authorizeQuote();
        CustomerContextRequest ctx = request.customerContext();
        PriceQuote result = (ctx == null || ctx.customerId() == null)
            ? pricingService.quote(request.planId(), request.region(), request.currency(),
                request.cycle(), request.couponCode())
            : pricingService.quoteWithCustomer(request.planId(), request.region(), request.currency(),
                request.cycle(), request.couponCode(),
                new CustomerContext(ctx.customerId(), Boolean.TRUE.equals(ctx.isNewCustomer())));
        return ResponseEntity.ok(ApiResponse.ok("Quote resolved.", toResponse(result)));
    }

    private PriceQuoteResponse toResponse(PriceQuote quote) {
        return new PriceQuoteResponse(
            quote.baseAmount(), quote.currency(), quote.discountAmount(), quote.finalAmount(),
            quote.reason(), quote.promotionId(), quote.appliedAction(), quote.conditionsSkipped(),
            quote.grantedEntitlement());
    }
}
