package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CustomerContextRequest;
import com.company.ppmsvc.api.dto.request.ReferralQuoteRequest;
import com.company.ppmsvc.api.dto.response.PriceQuoteResponse;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.PriceQuote;
import com.company.ppmsvc.referral.usecase.ReferralPricingService;
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
 * Referral Pricing Engine REST endpoint — separate from {@code
 * PriceQuoteController} (coupon path). Both return the same {@link
 * PriceQuoteResponse} shape so callers handle one response contract.
 *
 * <p>Base path: {@code /api/v1/ppm/referrals}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/referrals")
@RequiredArgsConstructor
@Tag(name = "Referral Pricing", description = "Referral Pricing Engine — resolve a plan's price with a referral code's reward applied.")
public class ReferralQuoteController {

    private final ReferralPricingService pricingService;

    @Operation(
        summary = "Resolve a price quote for a plan using a referral code",
        description = """
            Resolves the applicable price for the given plan/region/currency/cycle,
            then applies the reward promotion behind `referralCode`'s program.
            `customerContext` is required — referral pricing always evaluates conditions.

            Always returns HTTP 200. Inspect `reason` for the outcome — reuses the
            same reason vocabulary as `/quotes`, plus:

            - `referral_code_not_found`     — no active referral code with that code exists.
            - `referral_code_inactive`      — the code has been deactivated.
            - `referral_program_inactive`   — the program is inactive or misconfigured.

            Returns HTTP 404 only when the plan or its price cannot be resolved.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Quote resolved — inspect `reason` in the response body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Plan or price could not be resolved")
    })
    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<PriceQuoteResponse>> quote(
            @Valid @RequestBody ReferralQuoteRequest request) {
        log.debug("→ POST /api/v1/ppm/referrals/quote planId={} referralCode={}",
            request.planId(), request.referralCode());
        CustomerContextRequest ctx = request.customerContext();
        CustomerContext customer = new CustomerContext(ctx.customerId(), Boolean.TRUE.equals(ctx.isNewCustomer()));
        PriceQuote result = pricingService.quote(request.planId(), request.region(), request.currency(),
            request.cycle(), request.referralCode(), customer);
        return ResponseEntity.ok(ApiResponse.ok("Quote resolved.", toResponse(result)));
    }

    private PriceQuoteResponse toResponse(PriceQuote quote) {
        return new PriceQuoteResponse(
            quote.baseAmount(), quote.currency(), quote.discountAmount(), quote.finalAmount(),
            quote.reason(), quote.promotionId(), quote.appliedAction(), quote.conditionsSkipped(),
            quote.grantedEntitlement());
    }
}
