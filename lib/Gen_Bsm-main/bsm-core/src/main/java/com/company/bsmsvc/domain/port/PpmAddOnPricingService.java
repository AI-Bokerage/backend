package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.model.PpmResolvedAddOnPriceResult;
import java.util.UUID;

/**
 * Application service for PPM add-on price resolution.
 *
 * <p>Wraps {@link com.company.bsmsvc.infrastructure.client.ppm.PpmAddOnPricingClient}
 * with a Resilience4j circuit breaker so failures degrade gracefully
 * (circuit opens → fast-fail with {@link com.company.bsmsvc.domain.exception.PpmIntegrationException}).
 */
public interface PpmAddOnPricingService {

    PpmResolvedAddOnPriceResult resolveActivePrice(UUID addOnId, String region, String currency, BillingCycle cycle);
}
