package com.example.modauth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpCrmClient implements CrmClient {

    private final ObjectMapper objectMapper;

    @Value("${modauth.crm-base-url}")
    private String crmBaseUrl;

    @Value("${internal-service-secret}")
    private String internalServiceSecret;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * A lookup failure fails loud (502) rather than silently allowing
     * deactivation past an unchecked book — the whole point of this gate is
     * that it can't be skipped.
     */
    @Override
    public int activeAssignedLeadCount(UUID tenantId, UUID userId) {
        URI uri = URI.create(crmBaseUrl + "/internal/brokers/" + userId + "/lead-count?tenantId=" + tenantId);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("X-Internal-Secret", internalServiceSecret)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not check this person's assigned clients — try again");
            }
            JsonNode body = objectMapper.readTree(response.body());
            return body.path("data").path("count").asInt();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("crm.lead_count_check_failed userId={}", userId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not check this person's assigned clients — try again");
        }
    }

    /** Fails loud (502) like the lead count — a gate that can be skipped on an outage isn't a gate. */
    @Override
    public boolean isBrokerageProfileComplete(UUID tenantId) {
        URI uri = URI.create(crmBaseUrl + "/internal/tenants/" + tenantId + "/profile-status");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("X-Internal-Secret", internalServiceSecret)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not check the brokerage profile — try again");
            }
            return objectMapper.readTree(response.body()).path("data").path("complete").asBoolean(false);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("crm.profile_status_check_failed tenantId={}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not check the brokerage profile — try again");
        }
    }
}
