package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.persistence.mapper.ReferralCodePersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.ReferralCodeJpaRepository;
import com.company.ppmsvc.referral.model.ReferralCode;
import com.company.ppmsvc.referral.port.ReferralCodeRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReferralCodeRepositoryAdapter implements ReferralCodeRepositoryPort {

    private final ReferralCodeJpaRepository    jpaRepository;
    private final ReferralCodePersistenceMapper mapper;

    @Override
    public ReferralCode save(ReferralCode code) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(code)));
    }

    @Override
    public Optional<ReferralCode> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ReferralCode> findAll() {
        return mapper.toDomainList(jpaRepository.findAll());
    }

    @Override
    public Optional<ReferralCode> findByCode(String code) {
        return jpaRepository.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public boolean existsByProgramAndReferrer(UUID referralProgramId, String referrerCustomerId) {
        return jpaRepository.existsByReferralProgramIdAndReferrerCustomerId(referralProgramId, referrerCustomerId);
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.REFERRAL_CODE_NOT_FOUND,
                "Referral code not found or already deleted: " + id);
        }
    }
}
