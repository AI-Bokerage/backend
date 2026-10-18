package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.persistence.mapper.ReferralProgramPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.ReferralProgramJpaRepository;
import com.company.ppmsvc.referral.model.ReferralProgram;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReferralProgramRepositoryAdapter implements ReferralProgramRepositoryPort {

    private final ReferralProgramJpaRepository    jpaRepository;
    private final ReferralProgramPersistenceMapper mapper;

    @Override
    public ReferralProgram save(ReferralProgram program) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(program)));
    }

    @Override
    public Optional<ReferralProgram> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ReferralProgram> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByNameAsc());
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.REFERRAL_PROGRAM_NOT_FOUND,
                "Referral program not found or already deleted: " + id);
        }
    }
}
