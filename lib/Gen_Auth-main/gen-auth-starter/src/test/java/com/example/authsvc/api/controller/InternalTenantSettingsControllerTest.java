package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.TenantMfaRequiredRequest;
import com.example.authsvc.application.service.TenantSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalTenantSettingsControllerTest {

    @Mock private TenantSettingsService tenantSettingsService;

    @Test
    void setMfaRequired_delegatesToService() {
        InternalTenantSettingsController controller = new InternalTenantSettingsController(tenantSettingsService);
        UUID tenantId = UUID.randomUUID();

        ResponseEntity<Void> response = controller.setMfaRequired(tenantId, new TenantMfaRequiredRequest(true));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(tenantSettingsService).setMfaRequired(tenantId, true);
    }
}
