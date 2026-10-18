package com.company.bsmsvc.infrastructure.client.ppm;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmPlanVersionResult;
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
 * HTTP client for PPM-SVC plan version lookup (C2 version-locking).
 *
 * <p>Calls {@code GET /api/v1/ppm/plans/{planId}/versions/latest}.
 * This endpoint is public — no Authorization header is required.
 *
 * <p>Any non-2xx response or connection failure throws {@link PpmIntegrationException}.
 * Callers (service layer) apply the circuit breaker on top of this client.
 */
@Slf4j
@Component
public class PpmVersionClient {

    private static final String LATEST_VERSION_PATH = "/api/v1/ppm/plans/{planId}/versions/latest";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    public PpmVersionClient(
            @Qualifier("ppmRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the latest active version for a PPM plan.
     *
     * @param planId PPM plan UUID
     * @return latest version result
     * @throws PpmIntegrationException on any HTTP or connectivity failure
     */
    public PpmPlanVersionResult getLatestVersion(UUID planId) {
        log.debug("[PpmVersionClient] getLatestVersion planId={}", planId);
        try {
            String json = restClient.get()
                .uri(LATEST_VERSION_PATH, planId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("PPM version lookup returned " + resp.getStatusCode()
                        + " for planId=" + planId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("PPM version server error " + resp.getStatusCode()
                        + " for planId=" + planId);
                })
                .body(String.class);

            if (json == null) {
                throw new PpmIntegrationException("PPM version lookup returned empty response for planId=" + planId);
            }

            PpmApiEnvelope<PpmPlanVersionResult> envelope = objectMapper.readValue(
                json, new TypeReference<>() {});

            if (!envelope.success() || envelope.data() == null) {
                throw new PpmIntegrationException("PPM version lookup reported failure for planId=" + planId
                    + ": " + envelope.message());
            }

            log.debug("[PpmVersionClient] resolved planId={} versionId={} versionNo={}",
                planId, envelope.data().id(), envelope.data().versionNo());
            return envelope.data();

        } catch (PpmIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PpmVersionClient] getLatestVersion failed planId={}: {}", planId, e.getMessage());
            throw new PpmIntegrationException("PPM version lookup failed: " + e.getMessage(), e);
        }
    }
}
