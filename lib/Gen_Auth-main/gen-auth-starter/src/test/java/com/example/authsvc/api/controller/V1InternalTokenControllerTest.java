package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ImpersonationTokenService;
import com.example.authsvc.config.properties.InternalHmacAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V1InternalTokenControllerTest {

    @Mock private ImpersonationTokenService impersonationTokenService;

    private InternalHmacAuthProperties internalHmacAuthProperties;

    private V1InternalTokenController controller;

    @BeforeEach
    void setUp() {
        internalHmacAuthProperties = new InternalHmacAuthProperties();
        internalHmacAuthProperties.setTargetPaths(List.of("/v1/impersonation-token"));
        controller = new V1InternalTokenController(impersonationTokenService, null, internalHmacAuthProperties);
    }

    @Test
    void issueImpersonationToken_delegatesToServiceAndReturnsItsResponse() {
        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                UUID.randomUUID(), UUID.randomUUID(), "acme", UUID.randomUUID(), "session-abc", false);
        ImpersonationTokenResponse expected = new ImpersonationTokenResponse("jwt-value", 3600, "session-abc");
        when(impersonationTokenService.issue(request)).thenReturn(expected);

        ResponseEntity<ImpersonationTokenResponse> result = controller.issueImpersonationToken(request);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isEqualTo(expected);
        verify(impersonationTokenService).issue(eq(request));
    }
}
