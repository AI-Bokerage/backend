package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.CreditNoteEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CreditNoteJpaRepository extends JpaRepository<CreditNoteEntity, UUID>, JpaSpecificationExecutor<CreditNoteEntity> {
}
