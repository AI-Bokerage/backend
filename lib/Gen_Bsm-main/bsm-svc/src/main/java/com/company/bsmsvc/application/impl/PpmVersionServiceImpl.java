package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.port.PpmVersionService;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmPlanVersionResult;
import com.company.bsmsvc.infrastructure.client.ppm.PpmVersionClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PpmVersionServiceImpl implements PpmVersionService {

    private final PpmVersionClient ppmVersionClient;

    @Override
    @CircuitBreaker(name = "ppmVersion", fallbackMethod = "versionFallback")
    public PpmPlanVersionResult getLatestVersion(UUID ppmPlanId) {
        log.info("[PpmVersionService] getLatestVersion ppmPlanId={}", ppmPlanId);
        PpmPlanVersionResult result = ppmVersionClient.getLatestVersion(ppmPlanId);
        log.info("[PpmVersionService] resolved ppmPlanId={} versionId={} versionNo={}",
            ppmPlanId, result.id(), result.versionNo());
        return result;
    }

    private PpmPlanVersionResult versionFallback(UUID ppmPlanId, Exception ex) {
        log.error("[PpmVersionService] circuit open or failure ppmPlanId={}", ppmPlanId, ex);
        throw new PpmIntegrationException(
            "PPM version service unavailable for ppmPlanId=" + ppmPlanId
                + ": " + ex.getMessage(), ex);
    }
}
