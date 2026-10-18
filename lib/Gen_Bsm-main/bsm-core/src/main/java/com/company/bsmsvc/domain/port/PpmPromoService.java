package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PpmValidatePromoResult;
import java.util.UUID;

/**
 * Service abstraction over PPM-SVC's Promo Validation endpoint.
 *
 * <p>PPM is the sole authority for promo code validity. BSM never replicates
 * or re-evaluates promo rules (date ranges, usage caps, plan eligibility).
 * Implementations apply a circuit breaker — failure always means fail-closed
 * (promo cannot be applied), never silent approval.
 */
public interface PpmPromoService {

    /**
     * Validates a promo code against a PPM plan.
     *
     * @param code      promo code string
     * @param ppmPlanId PPM plan UUID
     * @return validation result; {@code valid=false} is a normal business outcome
     * @throws com.company.bsmsvc.domain.exception.PpmIntegrationException if PPM is unavailable
     *         or the circuit breaker is open
     */
    PpmValidatePromoResult validatePromo(String code, UUID ppmPlanId);
}
