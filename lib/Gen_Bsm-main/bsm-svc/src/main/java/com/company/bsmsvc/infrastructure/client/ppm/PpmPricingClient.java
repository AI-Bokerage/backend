package com.company.bsmsvc.infrastructure.client.ppm;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
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
 * HTTP client for PPM-SVC pricing resolution.
 *
 * <p>Calls {@code POST /api/v1/ppm/prices/resolve}. This endpoint is public —
 * no Authorization header is required.
 *
 * <p>Any non-2xx response or connection failure throws {@link PpmIntegrationException}.
 * Callers (service layer) apply the circuit breaker on top of this client.
 */
@Slf4j
@Component
public class PpmPricingClient {

    private static final String RESOLVE_PATH = "/api/v1/ppm/prices/resolve";

    private final RestClient    restClient;
    private final ObjectMapper  objectMapper;

    public PpmPricingClient(
            @Qualifier("ppmRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves the applicable price for a plan.
     *
     * @param planId   PPM plan UUID
     * @param region   region string (e.g. "INDIA") — normalised to uppercase by PPM
     * @param currency ISO 4217 currency code (e.g. "INR") — normalised to uppercase by PPM
     * @param cycle    billing cycle wire value: {@code "monthly"} or {@code "annual"}
     * @return resolved price result
     * @throws PpmIntegrationException on any HTTP or connectivity failure
     */
    public PpmResolvePriceResult resolve(UUID planId, String region, String currency, String cycle) {
        log.debug("[PpmPricingClient] resolve planId={} region={} currency={} cycle={}", planId, region, currency, cycle);
        PpmResolvePriceRequest body = new PpmResolvePriceRequest(planId, region, currency, cycle);
        try {
            String json = restClient.post()
                .uri(RESOLVE_PATH)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("PPM pricing returned " + resp.getStatusCode()
                        + " for planId=" + planId + " region=" + region + " currency=" + currency
                        + " cycle=" + cycle);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("PPM pricing server error " + resp.getStatusCode()
                        + " for planId=" + planId);
                })
                .body(String.class);

            if (json == null) {
                throw new PpmIntegrationException("PPM pricing returned empty response for planId=" + planId);
            }

            PpmApiEnvelope<PpmResolvePriceResult> envelope = objectMapper.readValue(
                json, new TypeReference<>() {});

            if (!envelope.success() || envelope.data() == null) {
                throw new PpmIntegrationException("PPM pricing resolve reported failure for planId=" + planId
                    + ": " + envelope.message());
            }

            log.debug("[PpmPricingClient] resolved planId={} amount={} currency={}", planId,
                envelope.data().amount(), envelope.data().currency());
            return envelope.data();

        } catch (PpmIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PpmPricingClient] resolve failed planId={}: {}", planId, e.getMessage());
            throw new PpmIntegrationException("PPM pricing resolve failed: " + e.getMessage(), e);
        }
    }
}
