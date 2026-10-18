package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.infrastructure.persistence.entity.PlatformInvoiceEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformInvoiceJpaRepository extends JpaRepository<PlatformInvoiceEntity, UUID>, JpaSpecificationExecutor<PlatformInvoiceEntity> {

    Optional<PlatformInvoiceEntity> findByInvoiceNumber(String invoiceNumber);

    List<PlatformInvoiceEntity> findByTenantId(UUID tenantId);

    List<PlatformInvoiceEntity> findBySubscriptionId(UUID subscriptionId);

    boolean existsBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID subscriptionId, Instant periodStart, Instant periodEnd);

    boolean existsBySubscriptionIdAndPeriodStartAndPeriodEndAndSourceIn(
        UUID subscriptionId, Instant periodStart, Instant periodEnd, Collection<InvoiceSource> sources);
}
