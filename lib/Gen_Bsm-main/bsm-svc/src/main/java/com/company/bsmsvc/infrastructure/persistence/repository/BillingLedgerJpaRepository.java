package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.BillingLedgerEntryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingLedgerJpaRepository extends JpaRepository<BillingLedgerEntryEntity, UUID> {
}
