package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.ReferralConversionRequest;
import com.company.ppmsvc.api.dto.response.ReferralRewardResponse;
import com.company.ppmsvc.referral.model.ReferralReward;
import com.company.ppmsvc.referral.usecase.ReferralConversionService;
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
 * Referral conversion endpoint. Called by checkout on successful subscription
 * to record the conversion and grant the referrer's reward.
 *
 * <p>Base path: {@code /api/v1/ppm/referrals}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/referrals")
@RequiredArgsConstructor
@Tag(name = "Referral Conversion", description = "Records referral conversions and grants referrer rewards.")
public class ReferralConversionController {

    private final ReferralConversionService conversionService;

    @Operation(summary = "Record a referral conversion and grant the referrer's reward",
        description = "Called by checkout on successful subscription. Idempotent per (referralCode, referredCustomerId).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Conversion recorded; reward granted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referral code or program not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Already converted for this customer, or referrer cap reached")
    })
    @PostMapping("/convert")
    public ResponseEntity<ApiResponse<ReferralRewardResponse>> convert(
            @Valid @RequestBody ReferralConversionRequest request) {
        log.debug("→ POST /api/v1/ppm/referrals/convert code={} referred={}",
            request.referralCode(), request.referredCustomerId());
        ReferralReward reward = conversionService.onConversion(request.referralCode(), request.referredCustomerId());
        return ResponseEntity.ok(
            ApiResponse.ok("Referral converted.",
                new ReferralRewardResponse(reward.promotionId(), reward.customerId(), reward.couponCode())));
    }
}
