package com.company.bsmsvc.infrastructure.client.ppm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PpmPricingClientTest {

    @Mock RestClient restClient;
    @Mock RestClient.RequestBodyUriSpec uriSpec;
    @Mock RestClient.RequestBodySpec bodySpec;
    @Mock RestClient.ResponseSpec responseSpec;

    ObjectMapper objectMapper;
    PpmPricingClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        client = new PpmPricingClient(restClient, objectMapper);
    }

    private void stubChain() {
        lenient().when(restClient.post()).thenReturn(uriSpec);
        lenient().when(uriSpec.uri(any(String.class))).thenReturn(bodySpec);
        lenient().when(bodySpec.contentType(any())).thenReturn(bodySpec);
        lenient().when(bodySpec.body(any())).thenReturn(bodySpec);
        lenient().when(bodySpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    @Test
    void resolve_throwsPpmIntegrationException_onNullResponse() {
        stubChain();
        lenient().when(responseSpec.body(any(Class.class))).thenReturn(null);

        assertThatThrownBy(() ->
            client.resolve(UUID.randomUUID(), "IN", "INR", "monthly"))
            .isInstanceOf(PpmIntegrationException.class);
    }

    @Test
    void resolve_throwsPpmIntegrationException_onSuccessFalseEnvelope() throws Exception {
        UUID planId = UUID.randomUUID();
        PpmApiEnvelope<PpmResolvePriceResult> envelope = new PpmApiEnvelope<>(false, "plan not found", null);
        String json = objectMapper.writeValueAsString(envelope);

        stubChain();
        lenient().when(responseSpec.body(any(Class.class))).thenReturn(json.getBytes());

        assertThatThrownBy(() -> client.resolve(planId, "IN", "INR", "monthly"))
            .isInstanceOf(PpmIntegrationException.class);
    }

    @Test
    void ppmResolvePriceResult_recordFields_areAccessible() {
        UUID planId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        PpmResolvePriceResult result = new PpmResolvePriceResult(
            planId, priceId, "monthly", "INR", "IN",
            new BigDecimal("999.00"), false, LocalDate.of(2026, 1, 1), true, 1L);

        assertThat(result.planId()).isEqualTo(planId);
        assertThat(result.priceId()).isEqualTo(priceId);
        assertThat(result.cycle()).isEqualTo("monthly");
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.amount()).isEqualByComparingTo("999.00");
        assertThat(result.active()).isTrue();
    }
}
