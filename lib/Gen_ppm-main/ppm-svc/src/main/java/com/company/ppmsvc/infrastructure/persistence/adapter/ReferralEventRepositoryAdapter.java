package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.persistence.mapper.ReferralEventPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.ReferralEventJpaRepository;
import com.company.ppmsvc.promotion.model.ReferralEventStatus;
import com.company.ppmsvc.referral.model.ReferralEvent;
import com.company.ppmsvc.referral.port.ReferralEventRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReferralEventRepositoryAdapter implements ReferralEventRepositoryPort {

    private final ReferralEventJpaRepository    jpaRepository;
    private final ReferralEventPersistenceMapper mapper;

    @Override
    public ReferralEvent save(ReferralEvent event) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(event)));
    }

    @Override
    public Optional<ReferralEvent> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ReferralEvent> findByCodeAndCustomer(UUID referralCodeId, String referredCustomerId) {
        return jpaRepository.findByReferralCodeIdAndReferredCustomerId(referralCodeId, referredCustomerId)
            .map(mapper::toDomain);
    }

    @Override
    public int countConvertedByCode(UUID referralCodeId) {
        return jpaRepository.countByReferralCodeIdAndStatus(referralCodeId, ReferralEventStatus.CONVERTED.getValue());
    }

    @Override
    public List<ReferralEvent> findAll() {
        return mapper.toDomainList(jpaRepository.findAll());
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.REFERRAL_EVENT_NOT_FOUND,
                "Referral event not found or already deleted: " + id);
        }
    }
}
