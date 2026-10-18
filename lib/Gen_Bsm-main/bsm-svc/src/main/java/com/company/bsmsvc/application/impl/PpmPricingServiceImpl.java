package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.infrastructure.client.ppm.PpmPricingClient;
import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PpmPricingServiceImpl implements PpmPricingService {

    private final PpmPricingClient ppmPricingClient;

    @Override
    @CircuitBreaker(name = "ppmPricing", fallbackMethod = "pricingFallback")
    public PpmResolvePriceResult resolvePrice(UUID ppmPlanId, String region, String currency, String cycle) {
        log.info("[PpmPricingService] resolve ppmPlanId={} region={} currency={} cycle={}",
            ppmPlanId, region, currency, cycle);
        PpmResolvePriceResult result = ppmPricingClient.resolve(ppmPlanId, region, currency, cycle);
        log.info("[PpmPricingService] resolved ppmPlanId={} amount={} currency={}",
            ppmPlanId, result.amount(), result.currency());
        return result;
    }

    private PpmResolvePriceResult pricingFallback(
            UUID ppmPlanId, String region, String currency, String cycle, Exception ex) {
        log.error("[PpmPricingService] circuit open or failure ppmPlanId={} region={} currency={} cycle={}",
            ppmPlanId, region, currency, cycle, ex);
        throw new PpmIntegrationException(
            "PPM pricing service unavailable for ppmPlanId=" + ppmPlanId
                + " region=" + region + " currency=" + currency + " cycle=" + cycle
                + ": " + ex.getMessage(), ex);
    }
}
