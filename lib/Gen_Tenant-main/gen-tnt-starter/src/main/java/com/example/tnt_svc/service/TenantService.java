// gen-tnt-starter/src/main/java/com/example/tnt_svc/service/TenantService.java
package com.example.tnt_svc.service;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.domain.exception.DuplicateSlugException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.persistence.TenantRepository;
import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import com.example.tnt_svc.web.dto.CreateTenantRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ProvisioningSagaOrchestrator orchestrator;

    public TenantService(TenantRepository tenantRepository, ProvisioningSagaOrchestrator orchestrator) {
        this.tenantRepository = tenantRepository;
        this.orchestrator = orchestrator;
    }

    public Tenant createTenant(CreateTenantRequest request) {
        if (request.idempotencyKey() != null) {
            var existing = tenantRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (tenantRepository.findBySlug(request.slug()).isPresent()) {
            throw new DuplicateSlugException(request.slug());
        }

        Tenant tenant = Tenant.builder()
            .slug(request.slug())
            .name(request.name())
            .status(TenantStatus.PROVISIONING)
            .region(request.region())
            .primaryOwnerUserId(request.primaryOwnerUserId())
            .idempotencyKey(request.idempotencyKey())
            .build();

        tenant = tenantRepository.save(tenant);
        orchestrator.startProvisioning(tenant.getId());
        return tenantRepository.findById(tenant.getId()).orElseThrow();
    }

    public Tenant getTenant(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> new TenantNotFoundException(id));
    }

    public Tenant getTenantBySlug(String slug) {
        return tenantRepository.findBySlug(slug).orElseThrow(() -> new TenantNotFoundException(slug));
    }

    public Tenant suspend(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.suspend();
        return tenantRepository.save(tenant);
    }

    public Tenant reactivate(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.reactivate();
        return tenantRepository.save(tenant);
    }

    public Tenant cancel(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.cancel();
        return tenantRepository.save(tenant);
    }

    public Tenant purge(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.purge();
        return tenantRepository.save(tenant);
    }
}
