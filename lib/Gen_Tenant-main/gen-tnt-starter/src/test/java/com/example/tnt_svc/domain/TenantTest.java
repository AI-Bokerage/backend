package com.example.tnt_svc.domain;

import com.example.tnt_svc.domain.exception.InvalidTenantStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantTest {

    private Tenant newTenant() {
        return Tenant.builder()
            .slug("acme")
            .name("Acme Corp")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build();
    }

    @Test
    void activateAfterProvisioningMovesFromProvisioningToActive() {
        Tenant tenant = newTenant();
        tenant.activateAfterProvisioning();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void suspendRequiresActiveStatus() {
        Tenant tenant = newTenant();
        assertThatThrownBy(tenant::suspend).isInstanceOf(InvalidTenantStateTransitionException.class);
    }

    @Test
    void suspendThenReactivateRoundTrips() {
        Tenant tenant = newTenant();
        tenant.activateAfterProvisioning();
        tenant.suspend();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        tenant.reactivate();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void purgeRequiresCancelledStatus() {
        Tenant tenant = newTenant();
        assertThatThrownBy(tenant::purge).isInstanceOf(InvalidTenantStateTransitionException.class);
    }

    @Test
    void cancelThenPurgeRoundTrips() {
        Tenant tenant = newTenant();
        tenant.cancel();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.CANCELLED);
        tenant.purge();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PURGED);
    }
}
