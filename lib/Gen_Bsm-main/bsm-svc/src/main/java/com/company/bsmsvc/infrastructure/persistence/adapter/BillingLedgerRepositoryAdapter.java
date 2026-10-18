package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.event.LedgerEntryCreatedEvent;
import com.company.bsmsvc.domain.model.BillingLedgerEntry;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.mapper.BillingLedgerEntryEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.BillingLedgerJpaRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingLedgerRepositoryAdapter implements BillingLedgerRepositoryPort {

    private final BillingLedgerJpaRepository billingLedgerJpaRepository;
    private final BillingLedgerEntryEntityMapper mapper;

    @Override
    @Transactional
    public BillingLedgerEntry save(BillingLedgerEntry entry) {
        var entity = mapper.toEntity(entry);
        var saved = billingLedgerJpaRepository.save(entity);
        BillingLedgerEntry domain = mapper.toDomain(saved);
        domain.registerEvent(new LedgerEntryCreatedEvent(
            domain.getId(), domain.getTenantId(), domain.getEntryType(), domain.getAmountMinor(), Instant.now()
        ));
        for (Object ev : domain.pullDomainEvents()) {
            if (ev instanceof LedgerEntryCreatedEvent e) {
                log.debug("LEDGER_ENTRY_CREATED id={} tenant={} type={}", e.ledgerEntryId(), e.tenantId(), e.entryType());
            }
        }
        return domain;
    }
}
