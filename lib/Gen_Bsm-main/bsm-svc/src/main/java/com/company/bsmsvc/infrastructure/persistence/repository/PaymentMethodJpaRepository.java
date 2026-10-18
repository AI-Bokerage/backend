package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.PaymentMethodEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodEntity, UUID> {
    List<PaymentMethodEntity> findByTenantId(UUID tenantId);
    Optional<PaymentMethodEntity> findByTenantIdAndIsDefaultTrue(UUID tenantId);
    boolean existsByTenantIdAndExternalPaymentMethodId(UUID tenantId, String externalPaymentMethodId);

    @Modifying
    @Query("UPDATE PaymentMethodEntity p SET p.isDefault = false WHERE p.tenantId = :tenantId")
    void unsetDefaultForTenant(@Param("tenantId") UUID tenantId);
}
