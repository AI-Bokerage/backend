package com.company.bsmsvc.infrastructure.client.ppm;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmDefaultTrialResult;
import com.company.bsmsvc.domain.port.DefaultTrialPlanPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client for the PPM default trial plan endpoint.
 *
 * <p>Calls {@code GET /api/v1/ppm/plans/default-trial}.
 * This endpoint is public — no Authorization header is required.
 *
 * <p>Used by {@link com.company.bsmsvc.messaging.TenantCreatedConsumer} to resolve
 * the canonical trial plan version when provisioning a new tenant's TRIALING
 * subscription without reading BSM's local plan catalog (E1.3.5).
 */
@Slf4j
@Component
public class PpmDefaultTrialClient implements DefaultTrialPlanPort {

    private static final String DEFAULT_TRIAL_PATH = "/api/v1/ppm/plans/default-trial";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    public PpmDefaultTrialClient(
            @Qualifier("ppmRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the default trial plan and its latest active version.
     *
     * @return trial plan metadata including the planVersionId to use as ppmPlanVersionId
     * @throws PpmIntegrationException on any HTTP or connectivity failure
     */
    public PpmDefaultTrialResult getDefaultTrialPlan() {
        log.debug("[PpmDefaultTrialClient] getDefaultTrialPlan");
        try {
            String json = restClient.get()
                .uri(DEFAULT_TRIAL_PATH)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("PPM default-trial returned " + resp.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("PPM default-trial server error " + resp.getStatusCode());
                })
                .body(String.class);

            if (json == null) {
                throw new PpmIntegrationException("PPM default-trial returned empty response");
            }

            PpmApiEnvelope<PpmDefaultTrialResult> envelope = objectMapper.readValue(
                json, new TypeReference<>() {});

            if (!envelope.success() || envelope.data() == null) {
                throw new PpmIntegrationException("PPM default-trial reported failure: " + envelope.message());
            }

            log.debug("[PpmDefaultTrialClient] resolved planCode={} planVersionId={}",
                envelope.data().planCode(), envelope.data().planVersionId());
            return envelope.data();

        } catch (PpmIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PpmDefaultTrialClient] getDefaultTrialPlan failed: {}", e.getMessage());
            throw new PpmIntegrationException("PPM default-trial lookup failed: " + e.getMessage(), e);
        }
    }
}
