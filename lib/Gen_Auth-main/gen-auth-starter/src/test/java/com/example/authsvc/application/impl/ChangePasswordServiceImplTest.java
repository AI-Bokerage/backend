package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ChangePasswordRequest;
import com.example.authsvc.common.exception.BadRequestException;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceImplTest {

    @Mock private AuthUserJpaRepository userRepo;
    @Mock private AuthSessionJpaRepository sessionRepo;
    @Mock private AuthRefreshTokenJpaRepository refreshTokenRepo;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private PasswordHasher passwordHasher;
    @Mock private AuthEventPublisher authEventPublisher;

    private ChangePasswordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChangePasswordServiceImpl(
                userRepo,
                sessionRepo,
                refreshTokenRepo,
                refreshTokenStore,
                passwordHasher,
                authEventPublisher
        );
    }

    @Test
    void changePasswordRevokesSessionsAndTokens() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                tenantId,
                "test-tenant",
                java.util.List.of(roleId),
                UserType.TENANT_USER,
                sessionId.toString(),
                Instant.now().plusSeconds(900),
                UUID.randomUUID().toString()
        );

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("user@cpms.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .roleId(roleId)
                .active(true)
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123",
                "NewPassword123!",
                "NewPassword123!"
        );

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(request.currentPassword(), user.getPasswordHash())).thenReturn(true);
        when(passwordHasher.hash(request.newPassword())).thenReturn("new-hash");
        UUID otherFamilyId = UUID.randomUUID();
        when(refreshTokenRepo.findActiveFamilyIdsByUserIdExcludingSession(userId, sessionId))
                .thenReturn(java.util.List.of(otherFamilyId));

        service.changePassword(principal, request);

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepo).save(user);
        verify(sessionRepo).deactivateAllByUserIdExceptSession(eq(userId), eq(sessionId), any(Instant.class));
        verify(refreshTokenRepo).revokeAllByUserIdExceptSession(eq(userId), eq(sessionId), any(Instant.class));
        verify(refreshTokenStore).revokeByFamilyId(otherFamilyId);
    }

    @Test
    void changePasswordRejectsInvalidCurrentPassword() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                UUID.randomUUID(),
                "test-tenant",
                java.util.List.of(UUID.randomUUID()),
                UserType.TENANT_USER,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900),
                UUID.randomUUID().toString()
        );

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(UUID.randomUUID())
                .email("user@cpms.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "WrongPassword123",
                "NewPassword123!",
                "NewPassword123!"
        );

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(request.currentPassword(), user.getPasswordHash())).thenReturn(false);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> service.changePassword(principal, request)
        );

        assertEquals("Current password is incorrect", ex.getMessage());
        verify(userRepo, never()).save(any(AuthUserEntity.class));
        verify(sessionRepo, never()).deactivateAllByUserIdExceptSession(any(UUID.class), any(UUID.class), any(Instant.class));
        verify(refreshTokenRepo, never()).revokeAllByUserIdExceptSession(any(UUID.class), any(UUID.class), any(Instant.class));
        verify(refreshTokenStore, never()).revokeByFamilyId(any(UUID.class));
    }

    @Test
    void changePasswordRejectsReusingCurrentPassword() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                UUID.randomUUID(),
                "test-tenant",
                java.util.List.of(UUID.randomUUID()),
                UserType.TENANT_USER,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900),
                UUID.randomUUID().toString()
        );

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(UUID.randomUUID())
                .email("user@cpms.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "SamePassword123!",
                "SamePassword123!",
                "SamePassword123!"
        );

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(request.currentPassword(), user.getPasswordHash())).thenReturn(true);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.changePassword(principal, request)
        );

        assertEquals("New password must be different from current password", ex.getMessage());
        verify(userRepo, never()).save(any(AuthUserEntity.class));
        verify(sessionRepo, never()).deactivateAllByUserIdExceptSession(any(UUID.class), any(UUID.class), any(Instant.class));
        verify(refreshTokenRepo, never()).revokeAllByUserIdExceptSession(any(UUID.class), any(UUID.class), any(Instant.class));
        verify(refreshTokenStore, never()).revokeByFamilyId(any(UUID.class));
    }

    @Test
    void changePassword_publishesPasswordChangedEvent() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                tenantId,
                "test-tenant",
                List.of(roleId),
                UserType.TENANT_USER,
                sessionId.toString(),
                Instant.now().plusSeconds(900),
                UUID.randomUUID().toString()
        );

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("user@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .roleId(roleId)
                .active(true)
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123", "NewPassword123!", "NewPassword123!");

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(request.currentPassword(), user.getPasswordHash())).thenReturn(true);
        when(passwordHasher.hash(request.newPassword())).thenReturn("new-hash");
        when(refreshTokenRepo.findActiveFamilyIdsByUserIdExcludingSession(userId, sessionId))
                .thenReturn(List.of());

        service.changePassword(principal, request);

        verify(authEventPublisher).publishPasswordChanged(eq(userId), eq(sessionId), any(Instant.class));
    }
}
