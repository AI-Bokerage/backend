package com.company.bsmsvc.infrastructure.client.ppm;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmValidatePromoResult;
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
 * HTTP client for PPM-SVC promo code validation.
 *
 * <p>Calls {@code POST /api/v1/ppm/promo-codes/validate}. This endpoint is public —
 * no Authorization header is required.
 *
 * <p>Note: PPM always returns HTTP 200 for validation responses (valid or invalid),
 * except HTTP 404 when the planId does not exist. Any non-2xx response or connection
 * failure throws {@link PpmIntegrationException}.
 */
@Slf4j
@Component
public class PpmPromoClient {

    private static final String VALIDATE_PATH = "/api/v1/ppm/promo-codes/validate";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;

    public PpmPromoClient(
            @Qualifier("ppmRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates a promo code against a PPM plan.
     *
     * @param code   promo code string (normalised to uppercase by PPM)
     * @param planId PPM plan UUID
     * @return validation result; {@code valid=false} is a normal business outcome, not an error
     * @throws PpmIntegrationException on HTTP 4xx/5xx or connectivity failure
     */
    public PpmValidatePromoResult validate(String code, UUID planId) {
        log.debug("[PpmPromoClient] validate code={} planId={}", code, planId);
        PpmValidatePromoRequest body = new PpmValidatePromoRequest(code, planId);
        try {
            String json = restClient.post()
                .uri(VALIDATE_PATH)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("PPM promo validation returned " + resp.getStatusCode()
                        + " for code=" + code + " planId=" + planId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("PPM promo server error " + resp.getStatusCode()
                        + " for code=" + code);
                })
                .body(String.class);

            if (json == null) {
                throw new PpmIntegrationException("PPM promo returned empty response for code=" + code);
            }

            PpmApiEnvelope<PpmValidatePromoResult> envelope = objectMapper.readValue(
                json, new TypeReference<>() {});

            if (envelope.data() == null) {
                throw new PpmIntegrationException("PPM promo validate returned null data for code=" + code);
            }

            log.debug("[PpmPromoClient] validated code={} valid={} reason={}", code,
                envelope.data().valid(), envelope.data().reason());
            return envelope.data();

        } catch (PpmIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PpmPromoClient] validate failed code={}: {}", code, e.getMessage());
            throw new PpmIntegrationException("PPM promo validation failed: " + e.getMessage(), e);
        }
    }
}
