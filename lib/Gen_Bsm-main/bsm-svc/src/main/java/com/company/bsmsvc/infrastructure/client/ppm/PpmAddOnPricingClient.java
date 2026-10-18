package com.company.bsmsvc.infrastructure.client.ppm;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmResolvedAddOnPriceResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * HTTP client for PPM-SVC add-on price resolution.
 *
 * <p>Calls {@code GET /api/v1/ppm/add-ons/{addOnId}/prices/active?region=...&currency=...&cycle=...}.
 * This endpoint is public — no Authorization header is required.
 *
 * <p>Any non-2xx response or connectivity failure throws {@link PpmIntegrationException}.
 * Callers apply the circuit breaker on top of this client.
 */
@Slf4j
@Component
public class PpmAddOnPricingClient {

    private static final String ACTIVE_PRICE_PATH = "/api/v1/ppm/add-ons/{addOnId}/prices/active";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    public PpmAddOnPricingClient(
            @Qualifier("ppmRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves the currently-effective active price for a PPM add-on.
     *
     * @param addOnId  PPM add-on UUID
     * @param region   region string (e.g. "INDIA")
     * @param currency ISO 4217 currency code (e.g. "INR")
     * @param cycle    BSM billing cycle (MONTHLY or YEARLY)
     * @return resolved add-on price result
     * @throws PpmIntegrationException on any HTTP or connectivity failure
     */
    public PpmResolvedAddOnPriceResult resolve(UUID addOnId, String region, String currency, BillingCycle cycle) {
        log.debug("[PpmAddOnPricingClient] resolve addOnId={} region={} currency={} cycle={}",
            addOnId, region, currency, cycle);

        String uri = UriComponentsBuilder.fromPath(ACTIVE_PRICE_PATH)
            .queryParam("region", region)
            .queryParam("currency", currency)
            .queryParam("cycle", cycle.name())
            .buildAndExpand(addOnId)
            .toUriString();

        try {
            String json = restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("PPM add-on pricing returned " + resp.getStatusCode()
                        + " for addOnId=" + addOnId + " region=" + region
                        + " currency=" + currency + " cycle=" + cycle);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("PPM add-on pricing server error " + resp.getStatusCode()
                        + " for addOnId=" + addOnId);
                })
                .body(String.class);

            if (json == null) {
                throw new PpmIntegrationException(
                    "PPM add-on pricing returned empty response for addOnId=" + addOnId);
            }

            PpmApiEnvelope<PpmResolvedAddOnPriceResult> envelope = objectMapper.readValue(
                json, new TypeReference<>() {});

            if (!envelope.success() || envelope.data() == null) {
                throw new PpmIntegrationException(
                    "PPM add-on pricing resolve reported failure for addOnId=" + addOnId
                        + ": " + envelope.message());
            }

            log.debug("[PpmAddOnPricingClient] resolved addOnId={} priceId={} amount={}",
                addOnId, envelope.data().priceId(), envelope.data().amount());
            return envelope.data();

        } catch (PpmIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PpmAddOnPricingClient] resolve failed addOnId={}: {}", addOnId, e.getMessage());
            throw new PpmIntegrationException("PPM add-on pricing resolve failed: " + e.getMessage(), e);
        }
    }
}
