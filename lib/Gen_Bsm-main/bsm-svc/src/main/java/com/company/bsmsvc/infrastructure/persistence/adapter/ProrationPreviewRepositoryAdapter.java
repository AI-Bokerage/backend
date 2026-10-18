package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.ProrationPreview;
import com.company.bsmsvc.domain.model.ProrationPreviewFilter;
import com.company.bsmsvc.domain.port.ProrationPreviewRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.ProrationPreviewEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.ProrationPreviewEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.ProrationPreviewJpaRepository;
import com.company.bsmsvc.infrastructure.persistence.specification.ProrationPreviewSpecifications;
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
public class ProrationPreviewRepositoryAdapter implements ProrationPreviewRepositoryPort {

    private final ProrationPreviewJpaRepository prorationPreviewJpaRepository;
    private final ProrationPreviewEntityMapper prorationPreviewEntityMapper;
    private final EntityManager entityManager;

    @Override
    public ProrationPreview save(ProrationPreview preview) {
        ProrationPreviewEntity entity = prorationPreviewEntityMapper.toEntity(preview);
        entity.setSubscription(entityManager.getReference(SubscriptionEntity.class, preview.getSubscriptionId()));
        return prorationPreviewEntityMapper.toDomain(prorationPreviewJpaRepository.save(entity));
    }

    @Override
    public Optional<ProrationPreview> findById(UUID id) {
        return prorationPreviewJpaRepository.findById(id)
            .map(prorationPreviewEntityMapper::toDomain);
    }

    @Override
    public PageResult<ProrationPreview> findPreviews(
        ProrationPreviewFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDirection
    ) {
        Sort sort = "desc".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();

        Page<ProrationPreviewEntity> result = prorationPreviewJpaRepository.findAll(
            ProrationPreviewSpecifications.withFilter(filter),
            PageRequest.of(page, size, sort)
        );

        List<ProrationPreview> content = result.getContent().stream()
            .map(prorationPreviewEntityMapper::toDomain)
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
