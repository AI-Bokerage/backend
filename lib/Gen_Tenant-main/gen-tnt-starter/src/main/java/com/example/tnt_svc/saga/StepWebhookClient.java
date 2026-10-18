// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/StepWebhookClient.java
package com.example.tnt_svc.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * POSTs to a configured step URL. Sync steps: the response decides success/fail
 * immediately. Async steps: any 2xx just means "accepted", not "done" — real
 * completion arrives later via the callback endpoint (see ProvisioningSagaOrchestrator).
 * A connection error on either mode is treated as an explicit failure, same as a
 * non-2xx response — never silence.
 */
@Component
public class StepWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(StepWebhookClient.class);

    private final RestClient restClient;

    public StepWebhookClient(RestClient.Builder restClientBuilder) {
        // ponytail: force HTTP/1.1 — the JDK HttpClient's default h2c upgrade
        // preflight trips up WireMock's Jetty server in tests (connection
        // reset/EOF on the very first request). Step webhooks are plain
        // internal HTTP/1.1 endpoints anyway, so there's no downside.
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        // Without a read timeout, a hung step target blocks driveNextStep indefinitely —
        // and for the SYNC-step path that's the create-tenant HTTP request thread, held
        // while it's also holding the tenant lock.
        org.springframework.http.client.JdkClientHttpRequestFactory requestFactory =
            new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder
            .requestFactory(requestFactory)
            .build();
    }

    public StepCallResult call(ProvisioningStepDefinition step, Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                .uri(step.url())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);
            return StepCallResult.success(body == null ? Map.of() : body);
        } catch (Exception e) {
            log.warn("Step call failed: step={} url={}", step.name(), step.url(), e);
            return StepCallResult.failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public void compensate(ProvisioningStepDefinition step, Map<String, Object> payload) {
        if (step.compensateUrl() == null || step.compensateUrl().isBlank()) {
            return;
        }
        try {
            restClient.post()
                .uri(step.compensateUrl())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Compensate call failed, ignoring (best-effort): step={} url={}", step.name(), step.compensateUrl(), e);
        }
    }
}
