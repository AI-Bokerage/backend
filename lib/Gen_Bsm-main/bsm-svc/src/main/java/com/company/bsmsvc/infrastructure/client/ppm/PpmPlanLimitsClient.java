package com.company.bsmsvc.infrastructure.client.ppm;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmPlanLimitsResult;
import com.company.bsmsvc.domain.port.PlanLimitsPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client for PPM-SVC plan version limits lookup (PPM-12B).
 *
 * <p>Calls {@code GET /api/v1/ppm/plan-versions/{versionId}/limits}.
 * This endpoint is public — no Authorization header is required.
 *
 * <p>Used by {@link com.company.bsmsvc.application.impl.CommercialEngineServiceImpl}
 * during {@code executeDowngradePreflight} for PPM-backed subscriptions.
 * Any non-2xx response or connection failure throws {@link PpmIntegrationException}.
 */
@Slf4j
@Component
public class PpmPlanLimitsClient implements PlanLimitsPort {

    private static final String LIMITS_PATH = "/api/v1/ppm/plan-versions/{versionId}/limits";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    public PpmPlanLimitsClient(
            @Qualifier("ppmRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns capacity limits for a PPM plan version.
     *
     * @param ppmPlanVersionId PPM plan version UUID
     * @return limits result; null fields mean unlimited
     * @throws PpmIntegrationException on any HTTP or connectivity failure
     */
    public PpmPlanLimitsResult getLimits(UUID ppmPlanVersionId) {
        log.debug("[PpmPlanLimitsClient] getLimits ppmPlanVersionId={}", ppmPlanVersionId);
        try {
            String json = restClient.get()
                .uri(LIMITS_PATH, ppmPlanVersionId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("PPM limits returned " + resp.getStatusCode()
                        + " for versionId=" + ppmPlanVersionId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("PPM limits server error " + resp.getStatusCode()
                        + " for versionId=" + ppmPlanVersionId);
                })
                .body(String.class);

            if (json == null) {
                throw new PpmIntegrationException("PPM limits returned empty response for versionId=" + ppmPlanVersionId);
            }

            PpmApiEnvelope<PpmPlanLimitsResult> envelope = objectMapper.readValue(
                json, new TypeReference<>() {});

            if (!envelope.success() || envelope.data() == null) {
                throw new PpmIntegrationException("PPM limits reported failure for versionId=" + ppmPlanVersionId
                    + ": " + envelope.message());
            }

            log.debug("[PpmPlanLimitsClient] resolved versionId={}", ppmPlanVersionId);
            return envelope.data();

        } catch (PpmIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PpmPlanLimitsClient] getLimits failed versionId={}: {}", ppmPlanVersionId, e.getMessage());
            throw new PpmIntegrationException("PPM limits lookup failed: " + e.getMessage(), e);
        }
    }
}
