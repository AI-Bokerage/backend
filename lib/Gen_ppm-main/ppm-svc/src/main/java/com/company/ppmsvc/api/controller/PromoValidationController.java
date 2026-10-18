package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.ValidatePromoCodeRequest;
import com.company.ppmsvc.api.dto.response.PromoValidationResponse;
import com.company.ppmsvc.promocode.model.PromoValidationResult;
import com.company.ppmsvc.promocode.usecase.PromoValidationService;
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
 * Promo Validation Engine REST endpoint (PPM-08).
 *
 * <p>A single {@code POST /validate} endpoint that answers: can this promo code
 * be applied to this plan right now, and if yes, what discount applies?
 *
 * <p>All promo validation failures are returned as HTTP 200 with {@code valid=false}
 * and a machine-readable {@code reason}. The only exception is an unknown
 * {@code planId}, which returns HTTP 404.
 *
 * <p>Base path: {@code /api/v1/ppm/promo-codes}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/promo-codes")
@RequiredArgsConstructor
@Tag(name = "Promo Validation", description = "Promo Validation Engine — validate a promo code against a plan.")
public class PromoValidationController {

    private final PromoValidationService promoValidationService;

    // ── POST /api/v1/ppm/promo-codes/validate ────────────────────────────────

    @Operation(
        summary = "Validate a promo code against a plan",
        description = """
            Determines whether the given promo code may be applied to the given plan.

            Always returns HTTP 200. Inspect `valid` to determine the outcome and
            `reason` for the wire-encoded validation result:

            - `valid`              — code may be applied; discount metadata is populated.
            - `promo_not_found`    — no active promo code with that code exists.
            - `promo_inactive`     — the promo code has been deactivated.
            - `promo_not_started`  — today is before the code's validFrom date.
            - `promo_expired`      — today is after the code's validUntil date.
            - `usage_cap_reached`  — all redemptions have been consumed.
            - `plan_not_eligible`  — the code is restricted to other plans.

            Note: `firstTimeOnly=true` is surfaced informally and does NOT cause rejection.
            Cross-service customer history verification is deferred to BSM checkout integration.

            Returns HTTP 404 only when the `planId` does not exist in the catalog.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Validation complete — inspect `valid` and `reason` in the response body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "The requested plan does not exist (PLAN_NOT_FOUND)")
    })
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<PromoValidationResponse>> validate(
            @Valid @RequestBody ValidatePromoCodeRequest request) {
        log.debug("→ POST /api/v1/ppm/promo-codes/validate code={} planId={}", request.code(), request.planId());
        PromoValidationResult result = promoValidationService.validate(request.code(), request.planId());
        return ResponseEntity.ok(
            ApiResponse.ok("Promo code validated.", toResponse(result)));
    }

    private PromoValidationResponse toResponse(PromoValidationResult result) {
        return new PromoValidationResponse(
            result.valid(), result.code(), result.promoCodeId(), result.discountType(),
            result.discountValue(), result.reason(), result.validUntil(), result.firstTimeOnly());
    }
}
