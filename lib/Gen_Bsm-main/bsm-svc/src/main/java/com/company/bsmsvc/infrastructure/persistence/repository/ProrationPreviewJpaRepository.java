package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.ProrationPreviewEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProrationPreviewJpaRepository
    extends JpaRepository<ProrationPreviewEntity, UUID>, JpaSpecificationExecutor<ProrationPreviewEntity> {
}
