package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.MfaService;
import com.example.authsvc.application.service.TenantSettingsService;
import com.example.authsvc.domain.port.MfaChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaLoginGateImplTest {

    @Mock private MfaService mfaService;
    @Mock private TenantSettingsService tenantSettingsService;
    @Mock private MfaChallengeStore challengeStore;

    private MfaLoginGateImpl gate;

    @Test
    void checkAndIssueChallenge_userEnrolled_returnsChallengeWithEnrollmentRequiredFalse() {
        gate = new MfaLoginGateImpl(mfaService, tenantSettingsService, challengeStore);
        UUID userId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder().id(userId).tenantId(UUID.randomUUID()).build();

        when(mfaService.isEnrolled(userId)).thenReturn(true);
        when(challengeStore.issue(userId, false, Duration.ofMinutes(5))).thenReturn("token-xyz");

        Optional<LoginResult> result = gate.checkAndIssueChallenge(user, "1.2.3.4", "curl/8.0");

        assertThat(result).isPresent();
        assertThat(result.get().mfaChallenge().challengeToken()).isEqualTo("token-xyz");
        assertThat(result.get().mfaChallenge().enrollmentRequired()).isFalse();
    }

    @Test
    void checkAndIssueChallenge_notEnrolledButTenantRequires_returnsChallengeWithEnrollmentRequiredTrue() {
        gate = new MfaLoginGateImpl(mfaService, tenantSettingsService, challengeStore);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder().id(userId).tenantId(tenantId).build();

        when(mfaService.isEnrolled(userId)).thenReturn(false);
        when(tenantSettingsService.isMfaRequired(tenantId)).thenReturn(true);
        when(challengeStore.issue(userId, true, Duration.ofMinutes(5))).thenReturn("token-abc");

        Optional<LoginResult> result = gate.checkAndIssueChallenge(user, "1.2.3.4", "curl/8.0");

        assertThat(result).isPresent();
        assertThat(result.get().mfaChallenge().enrollmentRequired()).isTrue();
    }

    @Test
    void checkAndIssueChallenge_neitherEnrolledNorRequired_returnsEmpty() {
        gate = new MfaLoginGateImpl(mfaService, tenantSettingsService, challengeStore);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder().id(userId).tenantId(tenantId).build();

        when(mfaService.isEnrolled(userId)).thenReturn(false);
        when(tenantSettingsService.isMfaRequired(tenantId)).thenReturn(false);

        Optional<LoginResult> result = gate.checkAndIssueChallenge(user, "1.2.3.4", "curl/8.0");

        assertThat(result).isEmpty();
    }
}
