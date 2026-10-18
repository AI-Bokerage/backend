package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaChallengeInfo;
import com.example.authsvc.application.service.MfaLoginGate;
import com.example.authsvc.application.service.MfaService;
import com.example.authsvc.application.service.TenantSettingsService;
import com.example.authsvc.domain.port.MfaChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Only registered when {@code app.mfa.enabled=true}. Implements the
 * design spec's login-time enforcement logic: enrolled users always
 * challenge; unenrolled users in an MFA-required tenant also challenge
 * (with {@code enrollmentRequired=true}); everyone else proceeds normally.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mfa", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MfaLoginGateImpl implements MfaLoginGate {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    private final MfaService mfaService;
    private final TenantSettingsService tenantSettingsService;
    private final MfaChallengeStore challengeStore;

    @Override
    public Optional<LoginResult> checkAndIssueChallenge(AuthUserEntity user, String ipAddress, String userAgent) {
        if (mfaService.isEnrolled(user.getId())) {
            String token = challengeStore.issue(user.getId(), false, CHALLENGE_TTL);
            log.info("mfa.login.challenge_issued userId={} enrollmentRequired=false", user.getId());
            return Optional.of(LoginResult.challenge(new MfaChallengeInfo(token, false)));
        }

        if (tenantSettingsService.isMfaRequired(user.getTenantId())) {
            String token = challengeStore.issue(user.getId(), true, CHALLENGE_TTL);
            log.info("mfa.login.challenge_issued userId={} enrollmentRequired=true", user.getId());
            return Optional.of(LoginResult.challenge(new MfaChallengeInfo(token, true)));
        }

        return Optional.empty();
    }
}
