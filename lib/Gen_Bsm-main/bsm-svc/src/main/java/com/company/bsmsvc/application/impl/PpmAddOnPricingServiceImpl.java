package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.port.PpmAddOnPricingService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.infrastructure.client.ppm.PpmAddOnPricingClient;
import com.company.bsmsvc.domain.model.PpmResolvedAddOnPriceResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PpmAddOnPricingServiceImpl implements PpmAddOnPricingService {

    private final PpmAddOnPricingClient ppmAddOnPricingClient;

    @Override
    @CircuitBreaker(name = "ppmAddOnPricing", fallbackMethod = "addOnPricingFallback")
    public PpmResolvedAddOnPriceResult resolveActivePrice(
            UUID addOnId, String region, String currency, BillingCycle cycle) {
        log.info("[PpmAddOnPricingService] resolve addOnId={} region={} currency={} cycle={}",
            addOnId, region, currency, cycle);
        PpmResolvedAddOnPriceResult result = ppmAddOnPricingClient.resolve(addOnId, region, currency, cycle);
        log.info("[PpmAddOnPricingService] resolved addOnId={} priceId={} amount={}",
            addOnId, result.priceId(), result.amount());
        return result;
    }

    private PpmResolvedAddOnPriceResult addOnPricingFallback(
            UUID addOnId, String region, String currency, BillingCycle cycle, Exception ex) {
        log.error("[PpmAddOnPricingService] circuit open or failure addOnId={} region={} currency={} cycle={}",
            addOnId, region, currency, cycle, ex);
        throw new PpmIntegrationException(
            "PPM add-on pricing service unavailable for addOnId=" + addOnId
                + " region=" + region + " currency=" + currency + " cycle=" + cycle
                + ": " + ex.getMessage(), ex);
    }
}
