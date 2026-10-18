package com.company.bsmsvc.infrastructure.client.ppm;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
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
 * HTTP client for PPM-SVC add-on catalog lookup.
 *
 * <p>Calls {@code GET /api/v1/ppm/add-ons/{id}}. This endpoint is public — no
 * Authorization header is required.
 *
 * <p>Used by {@link com.company.bsmsvc.messaging.BsmAddOnEventPublisher} to
 * enrich add-on activation/deactivation events with the add-on's {@code code}
 * and {@code type}, so downstream services (e.g. fmm-svc) can act on them
 * without needing their own PPM lookup. This is enrichment only — a failure
 * here must never block or roll back an add-on purchase that has already been
 * charged, so callers are expected to catch {@link PpmIntegrationException}
 * and proceed with a null code/type rather than propagate.
 */
@Slf4j
@Component
public class PpmAddOnCatalogClient {

    private static final String ADD_ON_PATH = "/api/v1/ppm/add-ons/{id}";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    public PpmAddOnCatalogClient(
            @Qualifier("ppmRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns catalog metadata (code, name, type) for a PPM add-on.
     *
     * @param ppmAddOnId PPM add-on UUID (stored on SubscriptionAddOn.ppmAddOnId)
     * @return add-on catalog metadata
     * @throws PpmIntegrationException on any HTTP or connectivity failure
     */
    public PpmAddOnCatalogResult getById(UUID ppmAddOnId) {
        log.debug("[PpmAddOnCatalogClient] getById ppmAddOnId={}", ppmAddOnId);
        try {
            String json = restClient.get()
                .uri(ADD_ON_PATH, ppmAddOnId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("PPM add-on lookup returned " + resp.getStatusCode()
                        + " for addOnId=" + ppmAddOnId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("PPM add-on lookup server error " + resp.getStatusCode()
                        + " for addOnId=" + ppmAddOnId);
                })
                .body(String.class);

            if (json == null) {
                throw new PpmIntegrationException("PPM add-on lookup returned empty response for addOnId=" + ppmAddOnId);
            }

            PpmApiEnvelope<PpmAddOnCatalogResult> envelope = objectMapper.readValue(
                json, new TypeReference<>() {});

            if (!envelope.success() || envelope.data() == null) {
                throw new PpmIntegrationException("PPM add-on lookup reported failure for addOnId=" + ppmAddOnId
                    + ": " + envelope.message());
            }

            log.debug("[PpmAddOnCatalogClient] resolved addOnId={} code={} type={}",
                ppmAddOnId, envelope.data().code(), envelope.data().type());
            return envelope.data();

        } catch (PpmIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PpmAddOnCatalogClient] getById failed addOnId={}: {}", ppmAddOnId, e.getMessage());
            throw new PpmIntegrationException("PPM add-on lookup failed: " + e.getMessage(), e);
        }
    }
}
