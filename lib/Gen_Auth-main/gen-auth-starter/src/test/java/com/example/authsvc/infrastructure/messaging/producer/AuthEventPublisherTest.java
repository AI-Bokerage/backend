package com.example.authsvc.infrastructure.messaging.producer;

import com.example.authsvc.config.properties.AuditRoutingProperties;
import com.example.authsvc.infrastructure.messaging.constant.AuthExchangeConstants;
import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthEventPublisherTest {

    @Mock private AuthOutboxEventJpaRepository outboxRepository;

    private AuthEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()),
                new AuditRoutingProperties());
    }

    @Test
    void publishLoginSuccess_writesPendingOutboxRowWithBusinessExchange() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        publisher.publishLoginSuccess(userId, tenantId, sessionId, "1.2.3.4", "curl/8.0");

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        AuthOutboxEventEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(AuthExchangeConstants.RK_LOGIN_SUCCESS);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getTargetExchange()).isNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPayload()).contains(userId.toString()).contains(tenantId.toString());
        assertThat(saved.getEventId()).isNotNull();
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void publishLoginFailed_hashesEmailInsteadOfStoringRaw() {
        publisher.publishLoginFailed("User@Example.com", "1.2.3.4", "INVALID_CREDENTIALS");

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload())
                .doesNotContain("user@example.com")
                .doesNotContain("User@Example.com");
    }

    @Test
    void publishLogout_writesOutboxRowWithLogoutRoutingKey() {
        UUID userId = UUID.randomUUID();
        publisher.publishLogout(userId, UUID.randomUUID());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_LOGOUT);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void publishPasswordChanged_writesOutboxRowWithPasswordChangedRoutingKey() {
        publisher.publishPasswordChanged(UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_PASSWORD_CHANGED);
    }

    @Test
    void publishImpersonationStarted_writesOutboxRowWithImpersonationStartedRoutingKey() {
        publisher.publishImpersonationStarted(UUID.randomUUID(), UUID.randomUUID(), "support", java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_IMPERSONATION_STARTED);
    }

    @Test
    void publishAuditTenantLoginSuccess_writesOutboxRowTargetingTenantAuditExchange() {
        AuditRoutingProperties routing = new AuditRoutingProperties();
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()), routing);

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        publisher.publishAuditTenantLoginSuccess(userId, tenantId, java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_AUDIT_TENANT_LOGIN_SUCCESS);
        assertThat(captor.getValue().getTargetExchange()).isEqualTo("auth.audit.tenant");
    }

    @Test
    void publishAuditPlatformLoginFailed_writesOutboxRowTargetingPlatformAuditExchange() {
        AuditRoutingProperties routing = new AuditRoutingProperties();
        routing.setPlatformExchange("cpms.platform.audit");
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()), routing);

        publisher.publishAuditPlatformLoginFailed(java.time.Instant.now(), "INVALID_CREDENTIALS");

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_AUDIT_PLATFORM_LOGIN_FAILED);
        assertThat(captor.getValue().getTargetExchange()).isEqualTo("cpms.platform.audit");
    }

    @Test
    void publishAuditLogout_writesOutboxRowTargetingTenantAuditExchange() {
        AuditRoutingProperties routing = new AuditRoutingProperties();
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()), routing);

        UUID userId = UUID.randomUUID();
        publisher.publishAuditLogout(userId, UUID.randomUUID(), java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_AUDIT_LOGOUT);
        assertThat(captor.getValue().getTargetExchange()).isEqualTo("auth.audit.tenant");
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void publishAuditPasswordChanged_writesOutboxRowTargetingTenantAuditExchange() {
        AuditRoutingProperties routing = new AuditRoutingProperties();
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()), routing);

        publisher.publishAuditPasswordChanged(UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_AUDIT_PASSWORD_CHANGED);
        assertThat(captor.getValue().getTargetExchange()).isEqualTo("auth.audit.tenant");
    }
}
