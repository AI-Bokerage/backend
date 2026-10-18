package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.port.PpmPromoService;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.infrastructure.client.ppm.PpmPromoClient;
import com.company.bsmsvc.domain.model.PpmValidatePromoResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PpmPromoServiceImpl implements PpmPromoService {

    private final PpmPromoClient ppmPromoClient;

    @Override
    @CircuitBreaker(name = "ppmPromo", fallbackMethod = "promoFallback")
    public PpmValidatePromoResult validatePromo(String code, UUID ppmPlanId) {
        log.info("[PpmPromoService] validate code={} ppmPlanId={}", code, ppmPlanId);
        PpmValidatePromoResult result = ppmPromoClient.validate(code, ppmPlanId);
        log.info("[PpmPromoService] validated code={} valid={} reason={}", code, result.valid(), result.reason());
        return result;
    }

    private PpmValidatePromoResult promoFallback(String code, UUID ppmPlanId, Exception ex) {
        log.error("[PpmPromoService] circuit open or failure code={} ppmPlanId={}", code, ppmPlanId, ex);
        throw new PpmIntegrationException(
            "PPM promo service unavailable for code=" + code
                + " ppmPlanId=" + ppmPlanId
                + ": " + ex.getMessage(), ex);
    }
}
