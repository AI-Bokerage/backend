package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.application.service.SuperAdminMagicLinkService;
import com.example.authsvc.common.exception.MagicLinkInvalidException;
import com.example.authsvc.common.exception.RateLimitExceededException;
import com.example.authsvc.config.properties.MagicLinkProperties;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SuperAdminMagicLinkServiceImpl implements SuperAdminMagicLinkService {

    private static final SecureRandom   SECURE_RANDOM = new SecureRandom();
    private static final int            TOKEN_BYTES   = 48;
    private static final Base64.Encoder URL_ENCODER   = Base64.getUrlEncoder().withoutPadding();

    private final MagicLinkProperties             props;
    private final MagicLinkStore                  magicLinkStore;
    private final InternalSessionRevocationService internalSessionRevocationService;
    private final PlatformSuperAdminJpaRepository superAdminRepo;
    private final PasswordHasher                  passwordHasher;
    private final AuditLogService                 auditLogService;
    private final EmailService                    emailService;

    @Override
    public MagicLinkIssueResponse issue(MagicLinkIssueRequest request, String ip) {
        String normalizedEmail = request.email().trim().toLowerCase();

        String rateLimitKey = "super-admin:" + ip + ":" + RefreshTokenHashUtil.hash(normalizedEmail);
        long attempts = magicLinkStore.incrementRateCounter(rateLimitKey, props.getRateLimitWindow());
        if (attempts > props.getRateLimitMaxRequests()) {
            log.warn("superadmin.magic_link.rate_limited ip={} attempts={}", ip, attempts);
            throw new RateLimitExceededException("Too many password reset requests. Please try again later.");
        }

        var superAdminOpt = superAdminRepo.findByEmailAndActiveTrue(normalizedEmail);
        if (superAdminOpt.isEmpty()) {
            log.info("superadmin.magic_link.ignored_unknown_email");
            return MagicLinkIssueResponse.generic();
        }

        var superAdmin = superAdminOpt.get();

        byte[] rawBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(rawBytes);
        String rawToken  = URL_ENCODER.encodeToString(rawBytes);
        String tokenHash = RefreshTokenHashUtil.hash(rawToken);

        Instant expiresAt = Instant.now().plus(props.getTtl());
        MagicLinkEntry entry = new MagicLinkEntry(
                superAdmin.getId(),
                TenantConstants.PLATFORM_TENANT_ID,
                MagicLinkEntry.PURPOSE_PASSWORD_RESET,
                expiresAt
        );
        magicLinkStore.save(tokenHash, entry, props.getTtl());
        log.info("superadmin.magic_link.issued userId={}", superAdmin.getId());

        String resetUrl = UriComponentsBuilder.fromUriString(props.getFrontendResetUrl())
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        emailService.sendPasswordResetLink(normalizedEmail, resetUrl);

        return MagicLinkIssueResponse.generic();
    }

    @Override
    @Transactional
    public MagicLinkVerifyResponse verify(MagicLinkVerifyRequest request) {
        String tokenHash = RefreshTokenHashUtil.hash(request.token());
        var entryOpt = magicLinkStore.find(tokenHash);

        if (entryOpt.isEmpty()) {
            log.info("superadmin.magic_link.invalid");
            throw new MagicLinkInvalidException();
        }

        MagicLinkEntry entry = entryOpt.get();

        if (Instant.now().isAfter(entry.expiresAt())) {
            magicLinkStore.delete(tokenHash);
            log.info("superadmin.magic_link.expired userId={}", entry.userId());
            throw new MagicLinkInvalidException();
        }

        var superAdmin = superAdminRepo.findById(entry.userId())
                .filter(PlatformSuperAdminEntity::isActive)
                .orElseThrow(() -> {
                    magicLinkStore.delete(tokenHash);
                    log.info("superadmin.magic_link.user_not_found_or_inactive userId={}", entry.userId());
                    return new MagicLinkInvalidException();
                });

        magicLinkStore.delete(tokenHash);

        superAdmin.setPasswordHash(passwordHasher.hash(request.newPassword()));
        superAdminRepo.save(superAdmin);

        UUID userId = superAdmin.getId();
        internalSessionRevocationService.revokeAllForUser(userId, TenantConstants.PLATFORM_TENANT_ID);

        auditLogService.log(new AuditLogRequest(
                TenantConstants.PLATFORM_TENANT_ID,
                userId,
                "superadmin.password.reset.success",
                null,
                null,
                "Super admin password reset via magic link"
        ));

        log.info("superadmin.password.reset.success userId={}", userId);
        return MagicLinkVerifyResponse.success();
    }
}
