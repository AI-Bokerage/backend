package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import com.company.bsmsvc.domain.exception.ConcurrentUpdateException;
import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.mapper.DunningAttemptEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.DunningAttemptJpaRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DunningAttemptRepositoryAdapter implements DunningAttemptRepositoryPort {

    private final DunningAttemptJpaRepository jpaRepository;
    private final DunningAttemptEntityMapper mapper;

    @Override @Transactional
    public DunningAttempt save(DunningAttempt attempt) {
        try {
            return mapper.toDomain(jpaRepository.save(mapper.toEntity(attempt)));
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            throw new ConcurrentUpdateException("DunningAttempt " + attempt.getId() + " was updated concurrently", e);
        }
    }

    @Override
    public Optional<DunningAttempt> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<DunningAttempt> findBySubscriptionId(UUID subscriptionId) {
        return jpaRepository.findBySubscriptionIdOrderByAttemptNumberAsc(subscriptionId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<DunningAttempt> findLatestBySubscriptionId(UUID subscriptionId) {
        return jpaRepository.findTopBySubscriptionIdOrderByAttemptNumberDesc(subscriptionId).map(mapper::toDomain);
    }

    @Override
    public List<DunningAttempt> findDuePending(Instant now) {
        return jpaRepository.findByStatusAndNextRetryAtBefore(DunningAttemptStatus.PENDING, now).stream().map(mapper::toDomain).toList();
    }
}
