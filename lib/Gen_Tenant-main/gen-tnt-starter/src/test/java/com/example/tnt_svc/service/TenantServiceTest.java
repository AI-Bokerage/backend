// gen-tnt-starter/src/test/java/com/example/tnt_svc/service/TenantServiceTest.java
package com.example.tnt_svc.service;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.domain.exception.DuplicateSlugException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.persistence.TenantRepository;
import com.example.tnt_svc.web.dto.CreateTenantRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final com.example.tnt_svc.saga.ProvisioningSagaOrchestrator orchestrator =
        mock(com.example.tnt_svc.saga.ProvisioningSagaOrchestrator.class);
    private final TenantService tenantService = new TenantService(tenantRepository, orchestrator);

    @Test
    void createTenantPersistsWithProvisioningStatus() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.empty());
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme", "us", UUID.randomUUID(), null);

        UUID generatedId = UUID.randomUUID();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(generatedId);
            return t;
        });
        when(tenantRepository.findById(generatedId)).thenAnswer(inv ->
            Optional.of(Tenant.builder().id(generatedId).slug("acme").status(TenantStatus.PROVISIONING).build()));

        Tenant tenant = tenantService.createTenant(request);

        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(tenant.getSlug()).isEqualTo("acme");
        verify(tenantRepository, times(1)).save(any(Tenant.class));
        verify(orchestrator, times(1)).startProvisioning(generatedId);
    }

    @Test
    void createTenantRejectsDuplicateSlug() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(mock(Tenant.class)));
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme", "us", UUID.randomUUID(), null);

        assertThatThrownBy(() -> tenantService.createTenant(request)).isInstanceOf(DuplicateSlugException.class);
    }

    @Test
    void createTenantWithSeenIdempotencyKeyReturnsExistingTenantWithoutSaving() {
        Tenant existing = Tenant.builder().slug("acme").idempotencyKey("key-1").status(TenantStatus.PROVISIONING).build();
        when(tenantRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme", "us", UUID.randomUUID(), "key-1");

        Tenant result = tenantService.createTenant(request);

        assertThat(result).isSameAs(existing);
        verify(tenantRepository, times(0)).save(any(Tenant.class));
    }

    @Test
    void getTenantThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(id)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void suspendTransitionsActiveTenantToSuspended() {
        UUID id = UUID.randomUUID();
        Tenant tenant = Tenant.builder().slug("acme").status(TenantStatus.ACTIVE).build();
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.suspend(id);

        assertThat(result.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }
}
