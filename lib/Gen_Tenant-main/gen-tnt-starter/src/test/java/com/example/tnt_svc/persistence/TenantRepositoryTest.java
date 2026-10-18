package com.example.tnt_svc.persistence;

import com.example.tnt_svc.AbstractIntegrationTest;
import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void savesAndFindsBySlug() {
        Tenant tenant = Tenant.builder()
            .slug("repo-test-slug")
            .name("Repo Test")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build();
        tenantRepository.save(tenant);

        assertThat(tenantRepository.findBySlug("repo-test-slug")).isPresent();
    }

    @Test
    void slugIsUnique() {
        Tenant first = Tenant.builder()
            .slug("dup-slug")
            .name("First")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build();
        tenantRepository.saveAndFlush(first);

        Tenant second = Tenant.builder()
            .slug("dup-slug")
            .name("Second")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build();

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> tenantRepository.saveAndFlush(second)
        );
    }

    @Test
    void findByIdempotencyKeyReturnsMatchingTenant() {
        Tenant tenant = Tenant.builder()
            .slug("idem-slug")
            .name("Idem Test")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .idempotencyKey("idem-key-1")
            .build();
        tenantRepository.save(tenant);

        assertThat(tenantRepository.findByIdempotencyKey("idem-key-1")).isPresent();
        assertThat(tenantRepository.findByIdempotencyKey("nonexistent")).isEmpty();
    }
}
