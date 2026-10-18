// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/StepWebhookClientTest.java
package com.example.tnt_svc.saga;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class StepWebhookClientTest {

    // One static server shared across all test methods in this class — reset
    // stubs before each test so a prior test's mapping for the same path
    // (callReturnsFailureOnNon2xx and callReturnsSuccessOn2xx both stub
    // POST /step) can't leak into a later test and flip its result.
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private final StepWebhookClient client = new StepWebhookClient(RestClient.builder());

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void callReturnsSuccessOn2xx() {
        // WireMock defaults an unset response Content-Type to
        // application/octet-stream — RestClient's body(Map.class) correctly
        // refuses to JSON-decode that, so the header must be explicit here.
        wireMock.stubFor(post(urlEqualTo("/step")).willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json").withBody("{\"ok\":true}")));
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", wireMock.baseUrl() + "/step", StepMode.SYNC, true, null);

        StepCallResult result = client.call(step, Map.of("tenantId", "abc"));

        assertThat(result.success()).isTrue();
    }

    @Test
    void callReturnsFailureOnNon2xx() {
        wireMock.stubFor(post(urlEqualTo("/step")).willReturn(aResponse().withStatus(500)));
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", wireMock.baseUrl() + "/step", StepMode.SYNC, true, null);

        StepCallResult result = client.call(step, Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotBlank();
    }

    @Test
    void callReturnsFailureOnConnectionError() {
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", "http://localhost:1/unreachable", StepMode.SYNC, true, null);

        StepCallResult result = client.call(step, Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotBlank();
    }

    @Test
    void compensateNeverThrowsEvenOnConnectionError() {
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", "http://localhost:1/unreachable", StepMode.SYNC, true, "http://localhost:1/compensate");

        client.compensate(step, Map.of());
        // no exception = pass
    }

    @Test
    void compensateCallsConfiguredUrl() {
        wireMock.stubFor(post(urlEqualTo("/compensate")).willReturn(aResponse().withStatus(200)));
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", wireMock.baseUrl() + "/step", StepMode.SYNC, true, wireMock.baseUrl() + "/compensate");

        client.compensate(step, Map.of());

        wireMock.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/compensate")));
    }
}
