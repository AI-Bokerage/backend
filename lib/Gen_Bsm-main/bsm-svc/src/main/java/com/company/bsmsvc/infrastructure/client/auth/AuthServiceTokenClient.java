package com.company.bsmsvc.infrastructure.client.auth;

import com.company.bsmsvc.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client for AUTH-SVC's service-token factory.
 *
 * <p>Calls {@code POST /internal/service-token} with the {@code X-Internal-Secret}
 * header (checked by AUTH-SVC's {@code InternalTokenAuthFilter}). Returns a
 * short-lived (5-minute) SUPER_ADMIN Bearer JWT for calling other services'
 * JWT-gated internal routes — USG-SVC's {@code /internal/*} routes require this
 * (unlike most other services' internal routes, which trust a shared
 * {@code X-Internal-Key}/{@code X-Internal-Secret} header directly).
 *
 * <p>The only other caller of this endpoint today is SUP-SVC (tenant-sync
 * against TNT-SVC) — this is the second, following the same pattern.
 */
@Slf4j
@Component
public class AuthServiceTokenClient {

    private static final String TOKEN_PATH     = "/internal/service-token";
    private static final String SECRET_HEADER  = "X-Internal-Secret";
    private static final String SERVICE_HEADER = "X-CPMS-Service";
    private static final String CALLER_NAME    = "bsm-svc";

    private final RestClient     restClient;
    private final AuthProperties properties;
    private final ObjectMapper   objectMapper;

    public AuthServiceTokenClient(
            @Qualifier("authRestClient") RestClient restClient,
            AuthProperties properties,
            ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.properties   = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * @return a ready-to-use {@code Authorization} header value (already prefixed
     *         "Bearer "), or {@code null} if AUTH-SVC is unreachable.
     */
    public String requestServiceToken() {
        try {
            String json = restClient.post()
                .uri(TOKEN_PATH)
                .header(SECRET_HEADER, properties.internalSecret())
                .header(SERVICE_HEADER, CALLER_NAME)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RestClientException("AUTH-SVC returned " + resp.getStatusCode() + " for service-token");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new RestClientException("AUTH-SVC server error " + resp.getStatusCode() + " for service-token");
                })
                .body(String.class);

            if (json == null) {
                log.warn("[AuthServiceTokenClient] empty response — cannot obtain service token");
                return null;
            }

            AuthServiceTokenResult result = objectMapper.readValue(json, AuthServiceTokenResult.class);
            return result.accessToken();

        } catch (Exception e) {
            log.warn("[AuthServiceTokenClient] failed to obtain service token: {}", e.getMessage());
            return null;
        }
    }
}
