package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.port.TenantTrialRecordRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.TenantTrialRecordEntity;
import com.company.bsmsvc.infrastructure.persistence.repository.TenantTrialRecordJpaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenantTrialRecordRepositoryAdapter implements TenantTrialRecordRepositoryPort {

    private final TenantTrialRecordJpaRepository jpaRepository;

    @Override
    public boolean existsByTenantId(UUID tenantId) {
        return jpaRepository.existsByTenantId(tenantId);
    }

    @Override
    public void markTrialConsumed(UUID tenantId, UUID subscriptionId, Instant consumedAt) {
        TenantTrialRecordEntity entity = TenantTrialRecordEntity.builder()
            .tenantId(tenantId)
            .subscriptionId(subscriptionId)
            .trialConsumedAt(consumedAt)
            .build();
        jpaRepository.save(entity);
    }
}
