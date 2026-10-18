package com.company.bsmsvc.infrastructure.client.usg;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client for USG-SVC's on-demand quota-limit seeding endpoint.
 *
 * <p>Calls {@code POST /internal/v1/usg/seed-limits} with an
 * {@code Authorization: Bearer <token>} header (USG-SVC's {@code /internal/*}
 * routes are JWT-gated, unlike most other services' — see
 * {@link com.company.bsmsvc.infrastructure.client.auth.AuthServiceTokenClient}).
 *
 * <p>tenantId travels in the BODY, not the {@code x-cpms-tenant-id} header:
 * USG-SVC's {@code edgeAuth} middleware projects the JWT's own {@code tenant_id}
 * claim onto that header, overwriting anything the caller sets — and the
 * SUPER_ADMIN service token this client uses carries a fixed placeholder
 * tenant_id (all-zero UUID), not the real target tenant. See usg-svc's
 * {@code SeedLimitsRequestSchema} for the corresponding server-side comment.
 *
 * <p>Returns {@code false} on any I/O failure — the caller must not let a USG
 * outage block subscription creation. The async {@code bsm.subscription.created}
 * event remains the fallback seeding path either way.
 */
@Slf4j
@Component
public class UsgLimitsClient {

    private static final String SEED_LIMITS_PATH = "/internal/v1/usg/seed-limits";

    private final RestClient restClient;

    public UsgLimitsClient(@Qualifier("usgRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** @return {@code true} if USG-SVC accepted the seed request. */
    public boolean seedLimits(UUID tenantId, UUID ppmPlanId, String bearerToken) {
        log.debug("[UsgLimitsClient] seedLimits tenantId={} ppmPlanId={}", tenantId, ppmPlanId);
        try {
            Map<String, Object> body = Map.of(
                "tenant_id",   tenantId.toString(),
                "ppm_plan_id", ppmPlanId.toString()
            );

            restClient.post()
                .uri(SEED_LIMITS_PATH)
                .header("Authorization", bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException(
                        "USG-SVC returned " + resp.getStatusCode() + " for tenantId=" + tenantId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException(
                        "USG-SVC server error " + resp.getStatusCode() + " for tenantId=" + tenantId);
                })
                .toBodilessEntity();

            log.info("[UsgLimitsClient] limits seeded tenantId={} ppmPlanId={}", tenantId, ppmPlanId);
            return true;

        } catch (Exception e) {
            log.warn("[UsgLimitsClient] seedLimits failed tenantId={}: {}", tenantId, e.getMessage());
            return false;
        }
    }
}
