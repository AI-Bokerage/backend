package com.example.admsvc.application.impl;

import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Test-only probe service, physically located in {@code application.impl} so
 * {@link com.example.admsvc.infrastructure.security.TenantContextAspect}'s
 * pointcut — which matches on package, not on nesting — actually intercepts
 * its {@code @Transactional} methods. Lives under src/test/java only; it
 * never ships in the production jar.
 */
@Service
public class ProbeService {

    private final RoleRepository roleRepository;

    public ProbeService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public List<RoleEntity> listRolesForCurrentTenant() {
        // In real services this would be tenant-scoped via the caller's
        // own tenantId; here we deliberately query ALL roles to prove
        // RLS — not application code — is what filters them.
        return roleRepository.findAll();
    }

    @Transactional
    public RoleEntity createRoleViaBootstrap(@TenantIdParam UUID tenantId, String name) {
        RoleEntity role = RoleEntity.builder().tenantId(tenantId).name(name).build();
        return roleRepository.saveAndFlush(role);
    }
}
