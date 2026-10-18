package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.common.exception.MagicLinkInvalidException;
import com.example.authsvc.common.exception.RateLimitExceededException;
import com.example.authsvc.config.properties.MagicLinkProperties;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminMagicLinkServiceImplTest {

    @Mock private MagicLinkProperties               props;
    @Mock private MagicLinkStore                    magicLinkStore;
    @Mock private InternalSessionRevocationService  internalSessionRevocationService;
    @Mock private PlatformSuperAdminJpaRepository   superAdminRepo;
    @Mock private PasswordHasher                    passwordHasher;
    @Mock private AuditLogService                   auditLogService;
    @Mock private EmailService                      emailService;

    @InjectMocks
    private SuperAdminMagicLinkServiceImpl service;

    @Test
    void issue_unknownEmail_returnsGenericResponseWithoutSendingEmail() {
        when(props.getRateLimitWindow()).thenReturn(Duration.ofHours(1));
        when(props.getRateLimitMaxRequests()).thenReturn(3);
        when(magicLinkStore.incrementRateCounter(anyString(), any())).thenReturn(1L);
        when(superAdminRepo.findByEmailAndActiveTrue("unknown@example.com")).thenReturn(Optional.empty());

        MagicLinkIssueResponse response = service.issue(
                new MagicLinkIssueRequest("unknown@example.com", null), "127.0.0.1");

        assertThat(response.message()).isEqualTo("If the account exists, a reset link has been sent.");
        verify(emailService, org.mockito.Mockito.never()).sendPasswordResetLink(anyString(), anyString());
    }

    @Test
    void issue_rateLimitExceeded_throws() {
        when(props.getRateLimitWindow()).thenReturn(Duration.ofHours(1));
        when(props.getRateLimitMaxRequests()).thenReturn(3);
        when(magicLinkStore.incrementRateCounter(anyString(), any())).thenReturn(4L);

        assertThatThrownBy(() -> service.issue(new MagicLinkIssueRequest("admin@example.com", null), "127.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void verify_invalidToken_throwsMagicLinkInvalid() {
        when(magicLinkStore.find(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(
                new MagicLinkVerifyRequest("bad-token", "NewPassw0rd!", "NewPassw0rd!")))
                .isInstanceOf(MagicLinkInvalidException.class);
    }

    @Test
    void verify_expiredToken_deletesEntryAndThrows() {
        UUID userId = UUID.randomUUID();
        MagicLinkEntry expired = new MagicLinkEntry(
                userId, TenantConstants.PLATFORM_TENANT_ID, MagicLinkEntry.PURPOSE_PASSWORD_RESET,
                Instant.now().minusSeconds(60));
        when(magicLinkStore.find(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verify(
                new MagicLinkVerifyRequest("expired-token", "NewPassw0rd!", "NewPassw0rd!")))
                .isInstanceOf(MagicLinkInvalidException.class);

        verify(magicLinkStore).delete(anyString());
    }

    @Test
    void verify_validToken_resetsPasswordAndRevokesSessionsUnderPlatformTenant() {
        UUID userId = UUID.randomUUID();
        MagicLinkEntry entry = new MagicLinkEntry(
                userId, TenantConstants.PLATFORM_TENANT_ID, MagicLinkEntry.PURPOSE_PASSWORD_RESET,
                Instant.now().plusSeconds(600));
        when(magicLinkStore.find(anyString())).thenReturn(Optional.of(entry));

        PlatformSuperAdminEntity superAdmin = PlatformSuperAdminEntity.builder()
                .id(userId).email("admin@example.com").passwordHash("old-hash").active(true).build();
        when(superAdminRepo.findById(userId)).thenReturn(Optional.of(superAdmin));
        when(passwordHasher.hash(anyString())).thenReturn("hashed");

        MagicLinkVerifyResponse response = service.verify(
                new MagicLinkVerifyRequest("valid-token", "NewPassw0rd!", "NewPassw0rd!"));

        assertThat(response.message()).isEqualTo("Password reset successful. Please login again.");
        verify(superAdminRepo).save(superAdmin);
        verify(internalSessionRevocationService).revokeAllForUser(userId, TenantConstants.PLATFORM_TENANT_ID);
        verify(magicLinkStore).delete(anyString());
    }
}
