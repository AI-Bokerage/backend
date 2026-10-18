package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import java.util.UUID;

/**
 * Service abstraction over PPM-SVC's Pricing Resolver endpoint.
 *
 * <p>Implementations apply a circuit breaker so that a degraded PPM-SVC
 * fast-fails checkout requests rather than hanging for the full request timeout.
 */
public interface PpmPricingService {

    /**
     * Resolves the applicable price for a plan.
     *
     * @param ppmPlanId PPM plan UUID
     * @param region    region string (e.g. "INDIA")
     * @param currency  ISO 4217 currency code (e.g. "INR")
     * @param cycle     billing cycle wire value: {@code "monthly"} or {@code "annual"}
     * @return the resolved price; never {@code null}
     * @throws com.company.bsmsvc.domain.exception.PpmIntegrationException if PPM is unavailable
     *         or the circuit breaker is open
     */
    PpmResolvePriceResult resolvePrice(UUID ppmPlanId, String region, String currency, String cycle);
}
