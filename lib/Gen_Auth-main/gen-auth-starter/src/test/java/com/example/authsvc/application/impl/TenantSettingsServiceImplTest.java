package com.example.authsvc.application.impl;

import com.example.authsvc.infrastructure.persistence.entity.TenantSettingsEntity;
import com.example.authsvc.infrastructure.persistence.repository.TenantSettingsJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSettingsServiceImplTest {

    @Mock private TenantSettingsJpaRepository tenantSettingsRepo;

    private TenantSettingsServiceImpl service;

    @Test
    void isMfaRequired_noRow_returnsFalse() {
        service = new TenantSettingsServiceImpl(tenantSettingsRepo);
        UUID tenantId = UUID.randomUUID();
        when(tenantSettingsRepo.findById(tenantId)).thenReturn(Optional.empty());

        assertThat(service.isMfaRequired(tenantId)).isFalse();
    }

    @Test
    void isMfaRequired_rowExistsWithFlagTrue_returnsTrue() {
        service = new TenantSettingsServiceImpl(tenantSettingsRepo);
        UUID tenantId = UUID.randomUUID();
        TenantSettingsEntity entity = TenantSettingsEntity.builder()
                .tenantId(tenantId).mfaRequired(true).updatedAt(java.time.Instant.now()).build();
        when(tenantSettingsRepo.findById(tenantId)).thenReturn(Optional.of(entity));

        assertThat(service.isMfaRequired(tenantId)).isTrue();
    }

    @Test
    void setMfaRequired_noExistingRow_createsNewRow() {
        service = new TenantSettingsServiceImpl(tenantSettingsRepo);
        UUID tenantId = UUID.randomUUID();
        when(tenantSettingsRepo.findById(tenantId)).thenReturn(Optional.empty());

        service.setMfaRequired(tenantId, true);

        ArgumentCaptor<TenantSettingsEntity> captor = ArgumentCaptor.forClass(TenantSettingsEntity.class);
        verify(tenantSettingsRepo).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().isMfaRequired()).isTrue();
    }

    @Test
    void setMfaRequired_existingRow_updatesFlag() {
        service = new TenantSettingsServiceImpl(tenantSettingsRepo);
        UUID tenantId = UUID.randomUUID();
        TenantSettingsEntity existing = TenantSettingsEntity.builder()
                .tenantId(tenantId).mfaRequired(false).updatedAt(java.time.Instant.now().minusSeconds(60)).build();
        when(tenantSettingsRepo.findById(tenantId)).thenReturn(Optional.of(existing));

        service.setMfaRequired(tenantId, true);

        ArgumentCaptor<TenantSettingsEntity> captor = ArgumentCaptor.forClass(TenantSettingsEntity.class);
        verify(tenantSettingsRepo).save(captor.capture());
        assertThat(captor.getValue().isMfaRequired()).isTrue();
    }
}
