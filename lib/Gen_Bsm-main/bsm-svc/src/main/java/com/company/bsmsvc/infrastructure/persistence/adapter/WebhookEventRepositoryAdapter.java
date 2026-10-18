package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.model.WebhookEvent;
import com.company.bsmsvc.domain.port.WebhookEventRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.mapper.WebhookEventEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.WebhookEventJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookEventRepositoryAdapter implements WebhookEventRepositoryPort {

    private final WebhookEventJpaRepository jpaRepository;
    private final WebhookEventEntityMapper mapper;

    @Override @Transactional
    public WebhookEvent save(WebhookEvent event) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(event)));
    }

    @Override
    public Optional<WebhookEvent> findByProviderAndExternalEventId(PaymentProvider provider, String externalEventId) {
        return jpaRepository.findByProviderAndExternalEventId(provider, externalEventId).map(mapper::toDomain);
    }
}
