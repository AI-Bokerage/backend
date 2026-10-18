package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.TenantSettingsService;
import com.example.authsvc.infrastructure.persistence.entity.TenantSettingsEntity;
import com.example.authsvc.infrastructure.persistence.repository.TenantSettingsJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSettingsServiceImpl implements TenantSettingsService {

    private final TenantSettingsJpaRepository tenantSettingsRepo;

    @Override
    public boolean isMfaRequired(UUID tenantId) {
        return tenantSettingsRepo.findById(tenantId)
                .map(TenantSettingsEntity::isMfaRequired)
                .orElse(false);
    }

    @Override
    public void setMfaRequired(UUID tenantId, boolean required) {
        TenantSettingsEntity entity = tenantSettingsRepo.findById(tenantId)
                .orElseGet(() -> TenantSettingsEntity.builder().tenantId(tenantId).build());
        entity.setMfaRequired(required);
        entity.setUpdatedAt(Instant.now());
        tenantSettingsRepo.save(entity);
        log.info("tenant_settings.mfa_required.updated tenantId={} required={}", tenantId, required);
    }
}
