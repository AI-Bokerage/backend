package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.infrastructure.persistence.entity.WebhookEventEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventJpaRepository extends JpaRepository<WebhookEventEntity, UUID> {
    Optional<WebhookEventEntity> findByProviderAndExternalEventId(PaymentProvider provider, String externalEventId);
}
