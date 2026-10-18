package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.persistence.entity.AuthRefreshTokenEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserRoleJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the fix for the 3 of 4 logout-event call sites that are immediately
 * followed by a throw within the ambient {@code @Transactional refresh()}
 * method — those publishes must run in a dedicated {@code REQUIRES_NEW}
 * transaction so the outbox row commits before the ambient transaction's
 * later rollback would otherwise discard it.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private AuthRefreshTokenJpaRepository refreshTokenAuditRepo;
    @Mock private AuthSessionJpaRepository sessionRepo;
    @Mock private AuthUserJpaRepository userRepo;
    @Mock private AuthUserRoleJpaRepository authUserRoleRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private AuditLogService auditLogService;
    @Mock private TenantSlugResolver tenantSlugResolver;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private PlatformTransactionManager txManager;
    @Mock private com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver userDisplayNameResolver;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(
                refreshTokenStore, refreshTokenAuditRepo, sessionRepo, userRepo,
                authUserRoleRepository, jwtUtils, auditLogService, tenantSlugResolver,
                userDisplayNameResolver, authEventPublisher, txManager);
    }

    @Test
    void naturalExpiry_publishesLogoutEventsInDedicatedRequiresNewTransaction() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Instant now = Instant.now();

        AuthRefreshTokenEntity auditRecord = AuthRefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .sessionId(sessionId)
                .familyId(familyId)
                .tokenHash("hash")
                .generation(0)
                .used(true)
                .expiresAt(now.minusSeconds(3600)) // already expired -> natural expiry path
                .build();

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(sessionId).userId(userId).tenantId(tenantId).active(true).build();

        when(refreshTokenStore.findByTokenHash(anyString())).thenReturn(Optional.empty());
        when(refreshTokenAuditRepo.findByTokenHash(anyString())).thenReturn(Optional.of(auditRecord));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        assertThrows(UnauthorizedException.class,
                () -> service.refresh("some-raw-token", "1.2.3.4", "junit-agent"));

        ArgumentCaptor<TransactionDefinition> defCaptor = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(txManager).getTransaction(defCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW, defCaptor.getValue().getPropagationBehavior());

        verify(authEventPublisher).publishLogout(userId, sessionId);
        verify(authEventPublisher).publishAuditLogout(any(UUID.class), any(UUID.class), any(Instant.class));
    }

    @Test
    void replayAttack_publishesLogoutEventsInDedicatedRequiresNewTransaction() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Instant now = Instant.now();

        AuthRefreshTokenEntity auditRecord = AuthRefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .sessionId(sessionId)
                .familyId(familyId)
                .tokenHash("hash")
                .generation(0)
                .used(true)
                .expiresAt(now.plusSeconds(3600)) // not yet expired -> replay attack path
                .build();

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(sessionId).userId(userId).tenantId(tenantId).active(true).build();

        when(refreshTokenStore.findByTokenHash(anyString())).thenReturn(Optional.empty());
        when(refreshTokenAuditRepo.findByTokenHash(anyString())).thenReturn(Optional.of(auditRecord));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        assertThrows(UnauthorizedException.class,
                () -> service.refresh("some-raw-token", "1.2.3.4", "junit-agent"));

        ArgumentCaptor<TransactionDefinition> defCaptor = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(txManager).getTransaction(defCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW, defCaptor.getValue().getPropagationBehavior());

        verify(authEventPublisher).publishLogout(userId, sessionId);
        verify(authEventPublisher).publishAuditLogout(any(UUID.class), any(UUID.class), any(Instant.class));
    }

    @Test
    void explicitLogout_doesNotUseDedicatedTransactionTemplate() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        com.example.authsvc.domain.model.RefreshToken cached =
                com.example.authsvc.domain.model.RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .tenantId(tenantId)
                        .sessionId(sessionId)
                        .familyId(familyId)
                        .tokenHash("hash")
                        .generation(0)
                        .build();

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(sessionId).userId(userId).tenantId(tenantId).active(true).build();

        when(refreshTokenStore.findByTokenHash(anyString())).thenReturn(Optional.of(cached));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));

        service.logout("some-raw-token", "1.2.3.4", "junit-agent");

        // The 4th call site (explicit logout()) is not followed by a throw, so it
        // must NOT be wrapped in the dedicated REQUIRES_NEW transaction template.
        verify(txManager, never()).getTransaction(any());
        verify(authEventPublisher).publishLogout(userId, sessionId);
        verify(authEventPublisher).publishAuditLogout(eq(userId), eq(tenantId), any(Instant.class));
    }

    @Test
    void refresh_populatesUsernameUserEmailTenantNameClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        com.example.authsvc.domain.model.RefreshToken cached =
                com.example.authsvc.domain.model.RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .tenantId(tenantId)
                        .sessionId(sessionId)
                        .familyId(UUID.randomUUID())
                        .tokenHash("hash")
                        .generation(0)
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .absoluteExpiresAt(Instant.now().plusSeconds(3600))
                        .build();

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(sessionId).userId(userId).tenantId(tenantId).active(true).build();

        com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity user =
                com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity.builder()
                        .id(userId).tenantId(tenantId).email("jane@example.com")
                        .userType(com.example.authsvc.domain.enums.UserType.TENANT_USER)
                        .active(true).build();

        when(refreshTokenStore.findByTokenHash(anyString())).thenReturn(Optional.of(cached));
        when(sessionRepo.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(session));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(java.util.List.of());
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("acme");
        when(tenantSlugResolver.resolveName(tenantId)).thenReturn("Acme Corp");
        when(userDisplayNameResolver.resolve(userId)).thenReturn("Jane Doe");
        when(jwtUtils.generateTokenPair(any(JwtClaims.class), any(Instant.class))).thenReturn(
                new com.example.authsvc.domain.model.TokenPair(
                        "access-token", "refresh-token",
                        Instant.now().plusSeconds(900), cached.getAbsoluteExpiresAt()));

        service.refresh("some-raw-token", "1.2.3.4", "junit-agent");

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateTokenPair(claimsCaptor.capture(), any(Instant.class));
        assertThat(claimsCaptor.getValue().username()).isEqualTo("Jane Doe");
        assertThat(claimsCaptor.getValue().userEmail()).isEqualTo("jane@example.com");
        assertThat(claimsCaptor.getValue().tenantName()).isEqualTo("Acme Corp");
    }
}
