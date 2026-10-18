package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.campaign.model.Campaign;
import com.company.ppmsvc.campaign.port.CampaignRepositoryPort;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.persistence.mapper.CampaignPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.CampaignJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignRepositoryAdapter implements CampaignRepositoryPort {

    private final CampaignJpaRepository    jpaRepository;
    private final CampaignPersistenceMapper mapper;

    @Override
    public Campaign save(Campaign campaign) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(campaign)));
    }

    @Override
    public Optional<Campaign> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Campaign> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByNameAsc());
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND,
                "Campaign not found or already deleted: " + id);
        }
    }
}
