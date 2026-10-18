package com.company.bsmsvc.infrastructure.client.ppm;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
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
 * HTTP client for PPM-SVC plan version metadata lookup (PPM-12A).
 *
 * <p>Calls {@code GET /api/v1/ppm/plan-versions/{versionId}/meta}.
 * This endpoint is public — no Authorization header is required.
 *
 * <p>Used by {@link com.company.bsmsvc.messaging.BsmSubscriptionEventPublisher}
 * to resolve the {@code planCode} for PPM-backed subscriptions (GAP-1 fix).
 * Any non-2xx response or connection failure throws {@link PpmIntegrationException}.
 */
@Slf4j
@Component
public class PpmVersionMetaClient implements PlanVersionMetaPort {

    private static final String META_PATH = "/api/v1/ppm/plan-versions/{versionId}/meta";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    public PpmVersionMetaClient(
            @Qualifier("ppmRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns metadata (including planCode) for a PPM plan version.
     *
     * @param ppmPlanVersionId PPM plan version UUID (stored on Subscription.ppmPlanVersionId)
     * @return version metadata including the parent plan's code
     * @throws PpmIntegrationException on any HTTP or connectivity failure
     */
    public PpmVersionMetaResult getVersionMeta(UUID ppmPlanVersionId) {
        log.debug("[PpmVersionMetaClient] getVersionMeta ppmPlanVersionId={}", ppmPlanVersionId);
        try {
            String json = restClient.get()
                .uri(META_PATH, ppmPlanVersionId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("PPM version meta returned " + resp.getStatusCode()
                        + " for versionId=" + ppmPlanVersionId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("PPM version meta server error " + resp.getStatusCode()
                        + " for versionId=" + ppmPlanVersionId);
                })
                .body(String.class);

            if (json == null) {
                throw new PpmIntegrationException("PPM version meta returned empty response for versionId=" + ppmPlanVersionId);
            }

            PpmApiEnvelope<PpmVersionMetaResult> envelope = objectMapper.readValue(
                json, new TypeReference<>() {});

            if (!envelope.success() || envelope.data() == null) {
                throw new PpmIntegrationException("PPM version meta reported failure for versionId=" + ppmPlanVersionId
                    + ": " + envelope.message());
            }

            log.debug("[PpmVersionMetaClient] resolved versionId={} planCode={}",
                ppmPlanVersionId, envelope.data().planCode());
            return envelope.data();

        } catch (PpmIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PpmVersionMetaClient] getVersionMeta failed versionId={}: {}", ppmPlanVersionId, e.getMessage());
            throw new PpmIntegrationException("PPM version meta lookup failed: " + e.getMessage(), e);
        }
    }
}
