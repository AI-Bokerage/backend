package com.company.bsmsvc.infrastructure.client.usg;

import com.company.bsmsvc.infrastructure.client.auth.AuthServiceTokenClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsgLimitsSeedingServiceTest {

    @Mock private AuthServiceTokenClient authServiceTokenClient;
    @Mock private UsgLimitsClient usgLimitsClient;

    @InjectMocks
    private UsgLimitsSeedingService usgLimitsSeedingService;

    @Test
    void seedLimits_nullTenantId_doesNothing() {
        usgLimitsSeedingService.seedLimits(null, UUID.randomUUID());
        verifyNoInteractions(authServiceTokenClient, usgLimitsClient);
    }

    @Test
    void seedLimits_nullPpmPlanId_doesNothing() {
        usgLimitsSeedingService.seedLimits(UUID.randomUUID(), null);
        verifyNoInteractions(authServiceTokenClient, usgLimitsClient);
    }

    @Test
    void seedLimits_tokenFetchFails_skipsUsgCallWithoutThrowing() {
        UUID tenantId = UUID.randomUUID();
        UUID ppmPlanId = UUID.randomUUID();
        when(authServiceTokenClient.requestServiceToken()).thenReturn(null);

        usgLimitsSeedingService.seedLimits(tenantId, ppmPlanId);

        verify(usgLimitsClient, never()).seedLimits(any(), any(), any());
    }

    @Test
    void seedLimits_happyPath_callsUsgWithFetchedToken() {
        UUID tenantId = UUID.randomUUID();
        UUID ppmPlanId = UUID.randomUUID();
        when(authServiceTokenClient.requestServiceToken()).thenReturn("Bearer test-token");
        when(usgLimitsClient.seedLimits(tenantId, ppmPlanId, "Bearer test-token")).thenReturn(true);

        usgLimitsSeedingService.seedLimits(tenantId, ppmPlanId);

        verify(usgLimitsClient).seedLimits(eq(tenantId), eq(ppmPlanId), eq("Bearer test-token"));
    }
}
