package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.MigrationPlan;
import com.company.bsmsvc.domain.model.MigrationPlanFilter;
import com.company.bsmsvc.domain.model.MigrationPlanItem;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.port.MigrationPlanRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.MigrationPlanEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.MigrationPlanItemEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.MigrationPlanEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.mapper.MigrationPlanItemEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.MigrationPlanItemJpaRepository;
import com.company.bsmsvc.infrastructure.persistence.repository.MigrationPlanJpaRepository;
import com.company.bsmsvc.infrastructure.persistence.specification.MigrationPlanSpecifications;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MigrationPlanRepositoryAdapter implements MigrationPlanRepositoryPort {

    private final MigrationPlanJpaRepository migrationPlanJpaRepository;
    private final MigrationPlanItemJpaRepository migrationPlanItemJpaRepository;
    private final MigrationPlanEntityMapper migrationPlanEntityMapper;
    private final MigrationPlanItemEntityMapper migrationPlanItemEntityMapper;
    private final EntityManager entityManager;

    @Override
    public MigrationPlan savePlan(MigrationPlan plan) {
        MigrationPlanEntity entity = migrationPlanEntityMapper.toEntity(plan);
        entity.setSubscription(entityManager.getReference(SubscriptionEntity.class, plan.getSubscriptionId()));
        return migrationPlanEntityMapper.toDomain(migrationPlanJpaRepository.save(entity));
    }

    @Override
    public MigrationPlanItem saveItem(MigrationPlanItem item) {
        MigrationPlanItemEntity entity = migrationPlanItemEntityMapper.toEntity(item);
        entity.setMigrationPlan(entityManager.getReference(MigrationPlanEntity.class, item.getMigrationPlanId()));
        return migrationPlanItemEntityMapper.toDomain(migrationPlanItemJpaRepository.save(entity));
    }

    @Override
    public Optional<MigrationPlan> findById(UUID id) {
        return migrationPlanJpaRepository.findById(id)
            .map(migrationPlanEntityMapper::toDomain);
    }

    @Override
    public List<MigrationPlanItem> findItemsByMigrationPlanId(UUID migrationPlanId) {
        return migrationPlanItemJpaRepository.findAllByMigrationPlanId(migrationPlanId)
            .stream()
            .map(migrationPlanItemEntityMapper::toDomain)
            .toList();
    }

    @Override
    public PageResult<MigrationPlan> findPlans(
        MigrationPlanFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDirection
    ) {
        Sort sort = "desc".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();

        Page<MigrationPlanEntity> result = migrationPlanJpaRepository.findAll(
            MigrationPlanSpecifications.withFilter(filter),
            PageRequest.of(page, size, sort)
        );

        List<MigrationPlan> content = result.getContent().stream()
            .map(migrationPlanEntityMapper::toDomain)
            .toList();

        return new PageResult<>(
            content,
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext()
        );
    }
}
