package com.company.bsmsvc.infrastructure.client.ppm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmValidatePromoResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PpmPromoClientTest {

    @Mock RestClient restClient;
    @Mock RestClient.RequestBodyUriSpec uriSpec;
    @Mock RestClient.RequestBodySpec bodySpec;
    @Mock RestClient.ResponseSpec responseSpec;

    ObjectMapper objectMapper;
    PpmPromoClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        client = new PpmPromoClient(restClient, objectMapper);
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
    void validate_throwsPpmIntegrationException_onNullResponse() {
        stubChain();
        lenient().when(responseSpec.body(any(Class.class))).thenReturn(null);

        assertThatThrownBy(() ->
            client.validate("PROMO10", UUID.randomUUID()))
            .isInstanceOf(PpmIntegrationException.class);
    }

    @Test
    void ppmValidatePromoResult_invalidResult_hasCorrectFields() {
        PpmValidatePromoResult result = new PpmValidatePromoResult(
            false, "PROMO10", null, null, "EXPIRED", false);

        assertThat(result.valid()).isFalse();
        assertThat(result.code()).isEqualTo("PROMO10");
        assertThat(result.reason()).isEqualTo("EXPIRED");
        assertThat(result.discountType()).isNull();
        assertThat(result.discountValue()).isNull();
    }

    @Test
    void ppmValidatePromoResult_validResult_hasCorrectFields() {
        PpmValidatePromoResult result = new PpmValidatePromoResult(
            true, "SAVE20", "percentage", new BigDecimal("20.00"), null, false);

        assertThat(result.valid()).isTrue();
        assertThat(result.discountType()).isEqualTo("percentage");
        assertThat(result.discountValue()).isEqualByComparingTo("20.00");
    }
}
