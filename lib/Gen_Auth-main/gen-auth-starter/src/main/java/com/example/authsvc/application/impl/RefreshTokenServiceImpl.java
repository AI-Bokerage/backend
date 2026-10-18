package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.api.dto.response.RefreshResponse;
import com.example.authsvc.api.dto.response.RefreshResult;
import com.example.authsvc.api.mapper.AuthRefreshTokenMapper;
import com.example.authsvc.api.mapper.RefreshTokenMapper;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.RefreshTokenService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.domain.model.RefreshToken;
import com.example.authsvc.domain.model.TokenPair;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.persistence.entity.AuthRefreshTokenEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserRoleJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /** Primary active-token store — Redis/Valkey. */
    private final RefreshTokenStore refreshTokenStore;

    /**
     * Retained for two purposes only:
     * <ol>
     *   <li>Forensic replay detection: check whether a token hash ever existed</li>
     *   <li>Append-only audit history: write each new token for forensics</li>
     * </ol>
     * This repository is never used for active-token lookups.
     */
    private final AuthRefreshTokenJpaRepository refreshTokenAuditRepo;

    private final AuthSessionJpaRepository  sessionRepo;
    private final AuthUserJpaRepository     userRepo;
    private final AuthUserRoleJpaRepository authUserRoleRepository;
    private final JwtUtils jwtUtils;
    private final AuditLogService auditLogService;
    private final TenantSlugResolver tenantSlugResolver;
    private final UserDisplayNameResolver userDisplayNameResolver;

    private final AuthEventPublisher authEventPublisher;

    /**
     * Dedicated {@code REQUIRES_NEW} transaction template for the event-publish
     * call sites in {@link #refresh(String, String, String)} that are immediately
     * followed by a throw within the ambient {@code @Transactional} method. Outbox
     * rows written by {@link AuthEventPublisher} otherwise commit (or roll back)
     * with whatever transaction is ambient — publishing them in their own
     * independent transaction here lets them commit before the ambient
     * transaction's later throw rolls everything else back.
     */
    private final TransactionTemplate eventPublishTxTemplate;

    public RefreshTokenServiceImpl(
            RefreshTokenStore refreshTokenStore,
            AuthRefreshTokenJpaRepository refreshTokenAuditRepo,
            AuthSessionJpaRepository sessionRepo,
            AuthUserJpaRepository userRepo,
            AuthUserRoleJpaRepository authUserRoleRepository,
            JwtUtils jwtUtils,
            AuditLogService auditLogService,
            TenantSlugResolver tenantSlugResolver,
            UserDisplayNameResolver userDisplayNameResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher,
            PlatformTransactionManager txManager) {
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenAuditRepo = refreshTokenAuditRepo;
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.authUserRoleRepository = authUserRoleRepository;
        this.jwtUtils = jwtUtils;
        this.auditLogService = auditLogService;
        this.tenantSlugResolver = tenantSlugResolver;
        this.userDisplayNameResolver = userDisplayNameResolver;
        this.authEventPublisher = authEventPublisher;
        this.eventPublishTxTemplate = new TransactionTemplate(txManager);
        this.eventPublishTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public RefreshResult refresh(String rawToken, String ip, String userAgent) {

        // Step 1: reject missing token immediately
        if (rawToken == null || rawToken.isBlank()) {
            log.debug("refresh.missing_token ip={}", ip);
            throw new UnauthorizedException();
        }

        long refreshStart = System.currentTimeMillis();
        log.debug("refresh.started ip={}", ip);

        String tokenHash = RefreshTokenHashUtil.hash(rawToken);
        Instant now = Instant.now();

        // Step 2: look up the active token in Redis (authoritative source)
        long t0 = System.currentTimeMillis();
        var cachedOpt = refreshTokenStore.findByTokenHash(tokenHash);
        log.info("perf.refresh.redis_lookup.ms={}", System.currentTimeMillis() - t0);
        log.debug("refresh.token_lookup found={}", cachedOpt.isPresent());

        if (cachedOpt.isEmpty()) {
            // Token absent from Redis. Consult the audit log to distinguish two very
            // different situations:
            //   1. Replay attack  — token was consumed by a valid rotation, then
            //      presented again BEFORE its expiresAt. Security incident.
            //   2. Natural expiry — Redis TTL simply elapsed. Normal lifecycle event.
            refreshTokenAuditRepo.findByTokenHash(tokenHash).ifPresent(auditRecord -> {
                if (auditRecord.getExpiresAt().isAfter(now)) {
                    // Token not yet expired but missing from Redis → it was already
                    // rotated and is being re-used. Genuine replay attack.
                    handleReplayAttack(auditRecord, ip, userAgent);
                } else {
                    // Token's expiresAt has passed — natural Redis TTL expiry.
                    // This is a normal session lifecycle event, not a security incident.
                    log.info("refresh.expired_naturally sessionId={} userId={} expiresAt={}",
                            auditRecord.getSessionId(), auditRecord.getUserId(),
                            auditRecord.getExpiresAt());
                    sessionRepo.findById(auditRecord.getSessionId()).ifPresent(s -> {
                        if (s.isActive()) {
                            s.setActive(false);
                            s.setRevokedAt(now);
                            sessionRepo.save(s);
                            log.info("session.revoked sessionId={} reason=natural_expiry",
                                    s.getId());
                            if (authEventPublisher != null) {
                                eventPublishTxTemplate.executeWithoutResult(status -> {
                                    authEventPublisher.publishLogout(auditRecord.getUserId(), auditRecord.getSessionId());
                                    authEventPublisher.publishAuditLogout(
                                            auditRecord.getUserId(), auditRecord.getTenantId(), Instant.now());
                                });
                            }
                        }
                    });
                }
            });
            throw new UnauthorizedException();
        }

        RefreshToken cached = cachedOpt.get();
        log.debug("refresh.token_valid sessionId={} generation={} familyId={}",
                cached.getSessionId(), cached.getGeneration(), cached.getFamilyId());

        // Step 3: validate the owning session is still active
        AuthSessionEntity session = sessionRepo.findByIdAndActiveTrue(cached.getSessionId())
                .orElseThrow(UnauthorizedException::new);

        // Step 4: validate the user is still active
        AuthUserEntity user = userRepo.findById(session.getUserId())
                .filter(AuthUserEntity::isActive)
                .orElseThrow(UnauthorizedException::new);

        // Step 5: atomically consume the old token from Redis before issuing new tokens
        refreshTokenStore.revokeByTokenHash(tokenHash);

        // Step 6: generate a new JWT access token + new opaque refresh token.
        // Re-read roles from auth_user_roles so any role changes since the last
        // login take effect at the next token boundary rather than carrying stale
        // roles forward indefinitely through refresh cycles.
        String tenantSlug = tenantSlugResolver.resolve(user.getTenantId());
        String tenantName = tenantSlugResolver.resolveName(user.getTenantId());
        String username    = userDisplayNameResolver.resolve(user.getId());
        List<UUID> roleIds = authUserRoleRepository.findRoleIdsByUserId(user.getId());
        JwtClaims claims = new JwtClaims(
                user.getId(), user.getTenantId(), tenantSlug,
                roleIds, user.getUserType(),
                now, now.plus(15, ChronoUnit.MINUTES), session.getId().toString(), null,
                username, user.getEmail(), tenantName);

        // ── Absolute session boundary enforcement ────────────────────────────
        // absoluteExpiresAt is null for tokens issued before this feature was
        // introduced; fall back to the token's own expiresAt in that case so
        // legacy tokens continue working until they naturally expire.
        Instant absoluteExpiry = (cached.getAbsoluteExpiresAt() != null)
                ? cached.getAbsoluteExpiresAt()
                : cached.getExpiresAt();

        if (!Duration.between(now, absoluteExpiry).isPositive()) {
            log.warn("refresh.absolute_expired sessionId={} familyId={} absoluteExpiresAt={} userId={}",
                    cached.getSessionId(), cached.getFamilyId(), absoluteExpiry, cached.getUserId());
            refreshTokenStore.revokeByFamilyId(cached.getFamilyId());
            sessionRepo.findById(cached.getSessionId()).ifPresent(s -> {
                s.setActive(false);
                s.setRevokedAt(now);
                sessionRepo.save(s);
                log.info("session.revoked sessionId={} reason=absolute_expiry", s.getId());
                if (authEventPublisher != null) {
                    eventPublishTxTemplate.executeWithoutResult(status -> {
                        authEventPublisher.publishLogout(cached.getUserId(), cached.getSessionId());
                        authEventPublisher.publishAuditLogout(
                                cached.getUserId(), cached.getTenantId(), Instant.now());
                    });
                }
            });
            throw new UnauthorizedException();
        }

        long t1 = System.currentTimeMillis();
        // fixedRefreshExpiry = absoluteExpiry  → tokenPair.refreshTokenExpiry() == absoluteExpiry
        TokenPair tokenPair = jwtUtils.generateTokenPair(claims, absoluteExpiry);
        log.info("perf.refresh.jwt_generate.ms={}", System.currentTimeMillis() - t1);

        // Step 7: store the new refresh token in Redis (same familyId, generation+1)
        // tokenPair.refreshTokenExpiry() == absoluteExpiry (passed above), so Redis TTL
        // = Duration.between(now, absoluteExpiry) — the remaining fixed session window.
        String newTokenHash = RefreshTokenHashUtil.hash(tokenPair.refreshToken());
        String deviceFingerprint = RefreshTokenHashUtil.hash(userAgent + "|" + ip);

        Duration remainingTtl = Duration.between(now, tokenPair.refreshTokenExpiry());
        log.info("session.absolute_expiry sessionId={} absoluteExpiresAt={} userId={}",
                cached.getSessionId(), tokenPair.refreshTokenExpiry(), cached.getUserId());
        log.info("refresh.remaining_ttl sessionId={} remainingTtlSeconds={}",
                cached.getSessionId(), remainingTtl.toSeconds());

        long t2 = System.currentTimeMillis();
        RefreshToken newCachedToken = RefreshTokenMapper.toRotatedToken(
                user.getId(), user.getTenantId(), session.getId(),
                newTokenHash, cached.getFamilyId(), cached.getGeneration() + 1,
                deviceFingerprint,
                tokenPair.refreshTokenExpiry(),   // expiresAt         = absoluteExpiry
                tokenPair.refreshTokenExpiry(),   // absoluteExpiresAt = absoluteExpiry (preserved)
                now);
        refreshTokenStore.save(newCachedToken);
        log.info("perf.refresh.rotation.ms={}", System.currentTimeMillis() - t2);

        // Step 8: append the new token to the JPA audit trail (write-only)
        refreshTokenAuditRepo.save(AuthRefreshTokenMapper.toRotatedEntity(newCachedToken));

        // Step 9: update session activity timestamp
        session.setLastActivityAt(now);
        sessionRepo.save(session);

        // Step 10: audit log
        auditLogService.log(new AuditLogRequest(
                user.getTenantId(), user.getId(), "TOKEN_REFRESHED",
                ip, userAgent, "sessionId=" + session.getId()));

        log.info("refresh.success userId={} sessionId={} generation={}",
                user.getId(), session.getId(), newCachedToken.getGeneration());
        log.info("perf.refresh.total.ms={} sessionId={}", System.currentTimeMillis() - refreshStart, session.getId());

        Duration accessTtl  = Duration.between(now, tokenPair.accessTokenExpiry());
        // refreshTokenTtl is the REMAINING window to the absolute session boundary,
        // not a fresh sliding TTL. This drives the cookie Max-Age correctly.
        return new RefreshResult(
                new RefreshResponse(tokenPair.accessTokenExpiry()),
                tokenPair.accessToken(), tokenPair.refreshToken(),
                accessTtl, remainingTtl);
    }

    /**
     * Replay attack: a token that was already consumed (found in JPA audit log but not in
     * Redis) has been presented again. This indicates token theft — revoke the entire token
     * family in Redis and deactivate the session to protect the legitimate user.
     */
    private void handleReplayAttack(AuthRefreshTokenEntity auditRecord, String ip, String userAgent) {
        log.warn("security.replay_attack sessionId={} familyId={} generation={} ip={}",
                auditRecord.getSessionId(), auditRecord.getFamilyId(),
                auditRecord.getGeneration(), ip);

        // Revoke all tokens in this family from Redis
        refreshTokenStore.revokeByFamilyId(auditRecord.getFamilyId());
        log.warn("refresh.family_revoked familyId={} reason=replay_attack",
                auditRecord.getFamilyId());

        // Deactivate the session in PostgreSQL
        sessionRepo.findById(auditRecord.getSessionId()).ifPresent(session -> {
            session.setActive(false);
            session.setRevokedAt(Instant.now());
            sessionRepo.save(session);
            log.warn("session.revoked sessionId={} reason=replay_attack familyId={}",
                    session.getId(), auditRecord.getFamilyId());
            if (authEventPublisher != null) {
                eventPublishTxTemplate.executeWithoutResult(status -> {
                    authEventPublisher.publishLogout(auditRecord.getUserId(), auditRecord.getSessionId());
                    authEventPublisher.publishAuditLogout(
                            auditRecord.getUserId(), auditRecord.getTenantId(), Instant.now());
                });
            }
        });

        auditLogService.log(new AuditLogRequest(
                auditRecord.getTenantId(), auditRecord.getUserId(),
                "REFRESH_TOKEN_REPLAY_DETECTED",
                ip, userAgent, "familyId=" + auditRecord.getFamilyId()));
    }

    @Override
    @Transactional
    public void logout(String rawToken, String ipAddress, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            log.debug("auth.logout.no_token ip={}", ipAddress);
            return; // idempotent — nothing to revoke
        }

        String tokenHash = RefreshTokenHashUtil.hash(rawToken);
        Instant now = Instant.now();

        var cachedOpt = refreshTokenStore.findByTokenHash(tokenHash);
        if (cachedOpt.isEmpty()) {
            log.info("auth.logout.token_not_active ip={}", ipAddress);
            return; // already expired or rotated — idempotent
        }

        RefreshToken cached = cachedOpt.get();

        refreshTokenStore.revokeByFamilyId(cached.getFamilyId());
        log.info("refresh.family_revoked familyId={} reason=logout", cached.getFamilyId());

        sessionRepo.findById(cached.getSessionId()).ifPresent(s -> {
            s.setActive(false);
            s.setRevokedAt(now);
            sessionRepo.save(s);
            log.info("session.revoked sessionId={} reason=logout", s.getId());
        });

        log.info("auth.logout userId={} sessionId={} ip={}",
                cached.getUserId(), cached.getSessionId(), ipAddress);

        if (authEventPublisher != null) {
            authEventPublisher.publishLogout(cached.getUserId(), cached.getSessionId());
            authEventPublisher.publishAuditLogout(cached.getUserId(), cached.getTenantId(), now);
        }

        auditLogService.log(new AuditLogRequest(
                cached.getTenantId(), cached.getUserId(), "LOGOUT",
                ipAddress, userAgent, "sessionId=" + cached.getSessionId()));
    }
}
