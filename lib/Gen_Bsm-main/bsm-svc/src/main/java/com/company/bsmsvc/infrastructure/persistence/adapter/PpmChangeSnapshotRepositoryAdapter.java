package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.PpmChangeSnapshot;
import com.company.bsmsvc.domain.port.PpmChangeSnapshotRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.PpmChangeSnapshotEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.PpmChangeSnapshotEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.PpmChangeSnapshotJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PpmChangeSnapshotRepositoryAdapter implements PpmChangeSnapshotRepositoryPort {

    private final PpmChangeSnapshotJpaRepository jpaRepository;
    private final PpmChangeSnapshotEntityMapper mapper;
    private final EntityManager entityManager;

    @Override
    public PpmChangeSnapshot save(PpmChangeSnapshot snapshot) {
        PpmChangeSnapshotEntity entity = mapper.toEntity(snapshot);
        entity.setSubscription(
            entityManager.getReference(SubscriptionEntity.class, snapshot.getSubscriptionId()));
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public List<PpmChangeSnapshot> findBySubscriptionId(UUID subscriptionId) {
        return jpaRepository.findBySubscription_IdOrderByChangedAtDesc(subscriptionId)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }
}
