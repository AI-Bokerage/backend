package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.application.service.MagicLinkService;
import com.example.authsvc.common.exception.MagicLinkInvalidException;
import com.example.authsvc.common.exception.RateLimitExceededException;
import com.example.authsvc.config.properties.MagicLinkProperties;
import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Implements the forgot-password / magic-link reset flow.
 * Gated by {@code app.magic-link.enabled=true} (validated at boot against
 * {@code app.email.enabled} by {@link com.example.authsvc.config.MagicLinkActivationValidator}).
 *
 * <h3>Issue flow</h3>
 * <ol>
 *   <li>Rate-limit check (Redis counter keyed by IP + email hash)</li>
 *   <li>Lookup user — always return generic 202 to prevent account enumeration</li>
 *   <li>Generate 48-byte cryptographically random token (Base64-URL encoded)</li>
 *   <li>Store SHA-256 hash in Redis with configured TTL</li>
 *   <li>Send email asynchronously (fire-and-forget)</li>
 * </ol>
 *
 * <h3>Verify flow</h3>
 * <ol>
 *   <li>Hash incoming token, look up in Redis</li>
 *   <li>Validate expiry; delete entry immediately (one-time use)</li>
 *   <li>Hash new password using existing Argon2 hasher; update user record</li>
 *   <li>Revoke all active sessions and refresh tokens for user</li>
 *   <li>Write audit log entry</li>
 * </ol>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.magic-link", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MagicLinkServiceImpl implements MagicLinkService {

    private static final SecureRandom   SECURE_RANDOM = new SecureRandom();
    private static final int            TOKEN_BYTES   = 48; // 384 bits
    private static final Base64.Encoder URL_ENCODER   = Base64.getUrlEncoder().withoutPadding();

    private final MagicLinkProperties            props;
    private final MagicLinkStore                 magicLinkStore;
    private final AuthUserJpaRepository          userRepo;
    private final PasswordHasher                 passwordHasher;
    private final AuditLogService                auditLogService;
    private final EmailService                   emailService;
    private final InternalSessionRevocationService internalSessionRevocationService;

    @Override
    public MagicLinkIssueResponse issue(MagicLinkIssueRequest request, String ip) {

        String normalizedEmail = request.email().trim().toLowerCase();

        String rateLimitKey = ip + ":" + RefreshTokenHashUtil.hash(normalizedEmail);
        long attempts = magicLinkStore.incrementRateCounter(rateLimitKey, props.getRateLimitWindow());
        if (attempts > props.getRateLimitMaxRequests()) {
            log.warn("magic_link.rate_limited ip={} attempts={}", ip, attempts);
            throw new RateLimitExceededException("Too many password reset requests. Please try again later.");
        }

        var userOpt = userRepo.findByEmailAndActiveTrue(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.info("magic_link.ignored_unknown_email");
            return MagicLinkIssueResponse.generic();
        }

        var user = userOpt.get();

        byte[] rawBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(rawBytes);
        String rawToken  = URL_ENCODER.encodeToString(rawBytes);
        String tokenHash = RefreshTokenHashUtil.hash(rawToken);

        Instant expiresAt = Instant.now().plus(props.getTtl());
        MagicLinkEntry entry = new MagicLinkEntry(
                user.getId(),
                user.getTenantId(),
                MagicLinkEntry.PURPOSE_PASSWORD_RESET,
                expiresAt
        );
        magicLinkStore.save(tokenHash, entry, props.getTtl());
        log.info("magic_link.issued userId={}", user.getId());

        String baseUrl = buildResetBaseUrl(props.getFrontendResetUrl(), request.tenantSlug());
        String resetUrl = baseUrl + "?token=" + rawToken;
        emailService.sendPasswordResetLink(normalizedEmail, resetUrl);

        return MagicLinkIssueResponse.generic();
    }

    private static String buildResetBaseUrl(String configuredUrl, String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return configuredUrl;
        }
        try {
            URI uri = new URI(configuredUrl);
            String host = tenantSlug + "." + uri.getHost();
            int port = uri.getPort();
            String authority = port > 0 ? host + ":" + port : host;
            return uri.getScheme() + "://" + authority + uri.getPath();
        } catch (Exception e) {
            log.warn("magic_link.reset_url_build_failed url={} slug={}", configuredUrl, tenantSlug, e);
            return configuredUrl;
        }
    }

    @Override
    @Transactional
    public MagicLinkVerifyResponse verify(MagicLinkVerifyRequest request) {

        String tokenHash = RefreshTokenHashUtil.hash(request.token());
        var entryOpt = magicLinkStore.find(tokenHash);

        if (entryOpt.isEmpty()) {
            log.info("magic_link.invalid");
            throw new MagicLinkInvalidException();
        }

        MagicLinkEntry entry = entryOpt.get();

        if (Instant.now().isAfter(entry.expiresAt())) {
            magicLinkStore.delete(tokenHash);
            log.info("magic_link.expired userId={}", entry.userId());
            throw new MagicLinkInvalidException();
        }

        var user = userRepo.findById(entry.userId())
                .filter(u -> u.isActive())
                .orElseThrow(() -> {
                    magicLinkStore.delete(tokenHash);
                    log.info("magic_link.user_not_found_or_inactive userId={}", entry.userId());
                    return new MagicLinkInvalidException();
                });

        magicLinkStore.delete(tokenHash);

        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        userRepo.save(user);

        UUID userId = user.getId();
        internalSessionRevocationService.revokeAllForUser(userId, user.getTenantId());

        auditLogService.log(new AuditLogRequest(
                user.getTenantId(),
                userId,
                "password.reset.success",
                null,
                null,
                "Password reset via magic link"
        ));

        log.info("password.reset.success userId={}", userId);
        return MagicLinkVerifyResponse.success();
    }
}
