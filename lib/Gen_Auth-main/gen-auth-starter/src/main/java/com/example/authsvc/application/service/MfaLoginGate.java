package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;

import java.util.Optional;

/**
 * Decides, for a password-verified {@code TENANT_USER}, whether login must
 * pause for MFA. Only registered when {@code app.mfa.enabled=true} — see
 * {@code MfaLoginGateImpl}. {@code LoginExecutionServiceImpl} holds this as
 * a nullable collaborator (same pattern as {@code AuthEventPublisher}
 * elsewhere in this codebase) so the shared login executor works whether or
 * not MFA is enabled, without depending on any MFA-specific bean directly.
 */
public interface MfaLoginGate {

    /**
     * @return present with a challenge {@link LoginResult} if MFA must be
     *         completed before real tokens are issued; empty if the caller
     *         should proceed to normal token issuance.
     */
    Optional<LoginResult> checkAndIssueChallenge(AuthUserEntity user, String ipAddress, String userAgent);
}
