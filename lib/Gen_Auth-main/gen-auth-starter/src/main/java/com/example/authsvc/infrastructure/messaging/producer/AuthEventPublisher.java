package com.example.authsvc.infrastructure.messaging.producer;

import com.example.authsvc.config.properties.AuditRoutingProperties;
import com.example.authsvc.infrastructure.messaging.constant.AuthExchangeConstants;
import com.example.authsvc.infrastructure.messaging.event.AuditLoginFailedEvent;
import com.example.authsvc.infrastructure.messaging.event.AuditLoginSuccessEvent;
import com.example.authsvc.infrastructure.messaging.event.AuditLogoutEvent;
import com.example.authsvc.infrastructure.messaging.event.AuditPasswordChangedEvent;
import com.example.authsvc.infrastructure.messaging.event.ImpersonationStartedEvent;
import com.example.authsvc.infrastructure.messaging.event.LoginFailedEvent;
import com.example.authsvc.infrastructure.messaging.event.LoginSuccessEvent;
import com.example.authsvc.infrastructure.messaging.event.LogoutEvent;
import com.example.authsvc.infrastructure.messaging.event.PasswordChangedEvent;
import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Only registered when {@code app.messaging.enabled=true} — see
 * {@code MessagingConfig} for the companion exchange beans. Every
 * {@code publishX}/{@code publishAuditX} method writes a {@code PENDING}
 * row to {@code auth_outbox_events} instead of publishing to RabbitMQ
 * directly — {@link com.example.authsvc.infrastructure.messaging.outbox.AuthOutboxRelayJob}
 * (a separate scheduled component) drains the table with retry/failure
 * handling. This means a broker outage no longer silently loses events:
 * rows accumulate as {@code PENDING} until the relay job can deliver them.
 *
 * <p>Serialization failures ARE propagated (not swallowed) — a payload that
 * cannot be serialized never gets a row. For call sites that invoke this
 * publisher synchronously inside their own transaction (e.g.
 * {@code ChangePasswordServiceImpl}, {@code ImpersonationTokenServiceImpl},
 * {@code RefreshTokenServiceImpl}), that failure rolls back the caller's
 * transaction rather than silently losing the event. {@code LoginExecutionServiceImpl}
 * is the exception: its login-success/failed publishes already run inside a
 * detached async executor block (unrelated to this change), so a serialization
 * failure there surfaces only as an uncaught exception on that background thread,
 * not a rolled-back login transaction.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final AuthOutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final AuditRoutingProperties auditRoutingProperties;

    public void publishLoginSuccess(UUID userId, UUID tenantId, UUID sessionId,
                                    String ip, String userAgent) {
        LoginSuccessEvent event = new LoginSuccessEvent(
                userId, tenantId, sessionId, ip, userAgent, Instant.now());
        enqueue(AuthExchangeConstants.RK_LOGIN_SUCCESS, event, userId, null);
    }

    /**
     * Email is SHA-256 hashed before inclusion in the event payload.
     * Raw PII must never appear in event messages.
     */
    public void publishLoginFailed(String email, String ip, String reason) {
        LoginFailedEvent event = new LoginFailedEvent(
                sha256(email), ip, reason, Instant.now());
        enqueue(AuthExchangeConstants.RK_LOGIN_FAILED, event, null, null);
    }

    public void publishLogout(UUID userId, UUID sessionId) {
        LogoutEvent event = new LogoutEvent(userId, sessionId, Instant.now());
        enqueue(AuthExchangeConstants.RK_LOGOUT, event, userId, null);
    }

    public void publishPasswordChanged(UUID userId, UUID sessionId, Instant timestamp) {
        PasswordChangedEvent event = new PasswordChangedEvent(userId, sessionId, timestamp);
        enqueue(AuthExchangeConstants.RK_PASSWORD_CHANGED, event, userId, null);
    }

    public void publishImpersonationStarted(UUID superAdminId, UUID tenantId,
                                             String reason, Instant expiresAt) {
        ImpersonationStartedEvent event = new ImpersonationStartedEvent(
                superAdminId, tenantId, reason, expiresAt, Instant.now());
        enqueue(AuthExchangeConstants.RK_IMPERSONATION_STARTED, event, superAdminId, null);
    }

    public void publishAuditTenantLoginSuccess(UUID userId, UUID tenantId, Instant occurredAt) {
        AuditLoginSuccessEvent event = new AuditLoginSuccessEvent(userId, tenantId, occurredAt);
        enqueue(AuthExchangeConstants.RK_AUDIT_TENANT_LOGIN_SUCCESS, event, userId,
                auditRoutingProperties.getTenantExchange());
    }

    public void publishAuditTenantLoginFailed(UUID tenantId, Instant occurredAt, String reason) {
        AuditLoginFailedEvent event = new AuditLoginFailedEvent(tenantId, occurredAt, reason);
        enqueue(AuthExchangeConstants.RK_AUDIT_TENANT_LOGIN_FAILED, event, null,
                auditRoutingProperties.getTenantExchange());
    }

    public void publishAuditPlatformLoginSuccess(UUID userId, Instant occurredAt) {
        AuditLoginSuccessEvent event = new AuditLoginSuccessEvent(userId, null, occurredAt);
        enqueue(AuthExchangeConstants.RK_AUDIT_PLATFORM_LOGIN_SUCCESS, event, userId,
                auditRoutingProperties.getPlatformExchange());
    }

    public void publishAuditPlatformLoginFailed(Instant occurredAt, String reason) {
        AuditLoginFailedEvent event = new AuditLoginFailedEvent(null, occurredAt, reason);
        enqueue(AuthExchangeConstants.RK_AUDIT_PLATFORM_LOGIN_FAILED, event, null,
                auditRoutingProperties.getPlatformExchange());
    }

    public void publishAuditLogout(UUID userId, UUID tenantId, Instant occurredAt) {
        AuditLogoutEvent event = new AuditLogoutEvent(userId, tenantId, occurredAt);
        enqueue(AuthExchangeConstants.RK_AUDIT_LOGOUT, event, userId,
                auditRoutingProperties.getTenantExchange());
    }

    public void publishAuditPasswordChanged(UUID userId, UUID tenantId, Instant occurredAt) {
        AuditPasswordChangedEvent event = new AuditPasswordChangedEvent(userId, tenantId, occurredAt);
        enqueue(AuthExchangeConstants.RK_AUDIT_PASSWORD_CHANGED, event, userId,
                auditRoutingProperties.getTenantExchange());
    }

    /**
     * Serializes {@code payload}, builds a {@code PENDING} outbox row, and saves it.
     * {@code targetExchange = null} means "publish to the default business exchange"
     * at relay time; a non-null value (used by the audit-tier methods added in a
     * later change to this class) overrides to a specific exchange.
     */
    private void enqueue(String routingKey, Object payload, UUID userId, String targetExchange) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("event.serialize_failed routingKey={} eventType={} reason={}",
                    routingKey, payload.getClass().getSimpleName(), e.getMessage(), e);
            throw new IllegalStateException("Failed to serialize event payload for routingKey=" + routingKey, e);
        }

        AuthOutboxEventEntity row = AuthOutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType(routingKey)
                .payload(json)
                .userId(userId)
                .targetExchange(targetExchange)
                .status("PENDING")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        outboxRepository.save(row);
        log.info("event.enqueued routingKey={} eventType={} targetExchange={}",
                routingKey, payload.getClass().getSimpleName(), targetExchange);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — this can never happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
