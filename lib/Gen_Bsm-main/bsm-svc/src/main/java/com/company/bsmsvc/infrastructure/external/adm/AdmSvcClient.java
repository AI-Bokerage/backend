package com.company.bsmsvc.infrastructure.external.adm;

import com.company.bsmsvc.domain.exception.UsageDataUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Internal HTTP client for ADM-SVC.
 *
 * <p>All requests are authenticated with {@code X-Internal-Secret} —
 * the same shared secret used across all platform services for
 * service-to-service internal API calls.</p>
 *
 * <p>On any HTTP error or connection failure this client throws
 * {@link UsageDataUnavailableException}. Callers decide whether to
 * propagate (fail-closed for downgrade preflight) or absorb (informational reads).</p>
 */
@Slf4j
@Component
public class AdmSvcClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final String SERVICE_NAME = "ADM-SVC";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public AdmSvcClient(
            @Value("${services.adm-svc.base-url:http://localhost:8082}") String baseUrl,
            @Value("${services.adm-svc.internal-secret:change-me-in-production}") String internalSecret,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(INTERNAL_SECRET_HEADER, internalSecret)
            .build();
    }

    /**
     * Fetches real-time user counts for the given tenant from ADM-SVC.
     *
     * @param tenantId the tenant to query
     * @return usage metrics with active and total counts for internal and client users
     * @throws UsageDataUnavailableException if ADM-SVC is unreachable or returns an error
     */
    public AdmUsageMetricsResponse getUsageMetrics(UUID tenantId) {
        log.debug("[AdmSvcClient] getUsageMetrics tenantId={}", tenantId);
        try {
            String json = restClient.get()
                .uri("/internal/tenants/{tenantId}/usage-metrics", tenantId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException(
                        "ADM-SVC returned " + resp.getStatusCode() + " for usage-metrics tenantId=" + tenantId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException(
                        "ADM-SVC server error " + resp.getStatusCode() + " for usage-metrics tenantId=" + tenantId);
                })
                .body(String.class);

            if (json == null) {
                throw new UsageDataUnavailableException(SERVICE_NAME, "empty response body for tenantId=" + tenantId);
            }

            AdmUsageMetricsResponse response = objectMapper.readValue(json, AdmUsageMetricsResponse.class);
            log.debug("[AdmSvcClient] tenantId={} activeInternal={} activeClient={}",
                tenantId, response.activeInternalUsers(), response.activeClientUsers());
            return response;

        } catch (UsageDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AdmSvcClient] getUsageMetrics failed tenantId={}: {}", tenantId, e.getMessage());
            throw new UsageDataUnavailableException(SERVICE_NAME, e);
        }
    }
}
