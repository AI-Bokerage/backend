package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.LimitSnapshotFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionLimitSnapshot;
import com.company.bsmsvc.domain.port.SubscriptionLimitSnapshotRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionLimitSnapshotEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.SubscriptionLimitSnapshotEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.SubscriptionLimitSnapshotJpaRepository;
import com.company.bsmsvc.infrastructure.persistence.specification.SubscriptionLimitSnapshotSpecifications;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubscriptionLimitSnapshotRepositoryAdapter implements SubscriptionLimitSnapshotRepositoryPort {

    private final SubscriptionLimitSnapshotJpaRepository snapshotJpaRepository;
    private final SubscriptionLimitSnapshotEntityMapper snapshotEntityMapper;
    private final EntityManager entityManager;

    @Override
    public SubscriptionLimitSnapshot save(SubscriptionLimitSnapshot snapshot) {
        SubscriptionLimitSnapshotEntity entity = snapshotEntityMapper.toEntity(snapshot);
        entity.setSubscription(entityManager.getReference(SubscriptionEntity.class, snapshot.getSubscriptionId()));
        return snapshotEntityMapper.toDomain(snapshotJpaRepository.save(entity));
    }

    @Override
    public PageResult<SubscriptionLimitSnapshot> findSnapshots(
        LimitSnapshotFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDirection
    ) {
        Sort sort = "desc".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();

        Page<SubscriptionLimitSnapshotEntity> result = snapshotJpaRepository.findAll(
            SubscriptionLimitSnapshotSpecifications.withFilter(filter),
            PageRequest.of(page, size, sort)
        );

        List<SubscriptionLimitSnapshot> content = result.getContent().stream()
            .map(snapshotEntityMapper::toDomain)
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
