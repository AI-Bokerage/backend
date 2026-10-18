package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.api.dto.request.LoginAttemptRequest;
import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.mapper.AuthRefreshTokenMapper;
import com.example.authsvc.api.mapper.AuthSessionMapper;
import com.example.authsvc.api.mapper.LoginResponseMapper;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.LoginAttemptService;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.application.service.MfaLoginGate;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.domain.model.RefreshToken;
import com.example.authsvc.domain.model.TokenPair;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserRoleJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class LoginExecutionServiceImpl implements LoginExecutionService {

    private final AuthSessionJpaRepository      sessionRepo;
    private final AuthRefreshTokenJpaRepository refreshTokenAuditRepo;
    private final AuthUserRoleJpaRepository     authUserRoleRepository;
    private final RefreshTokenStore             refreshTokenStore;
    private final LoginAttemptService           loginAttemptService;
    private final LockoutService                lockoutService;
    private final AuditLogService               auditLogService;
    private final JwtUtils                      jwtUtils;
    private final PasswordHasher                passwordHasher;
    private final Executor                      asyncExecutor;
    private final TransactionTemplate           txTemplate;
    private final TenantSlugResolver            tenantSlugResolver;
    private final AuthEventPublisher            authEventPublisher;
    private final MfaLoginGate                  mfaLoginGate;
    private final UserDisplayNameResolver       userDisplayNameResolver;

    public LoginExecutionServiceImpl(
            AuthSessionJpaRepository sessionRepo,
            AuthRefreshTokenJpaRepository refreshTokenAuditRepo,
            AuthUserRoleJpaRepository authUserRoleRepository,
            RefreshTokenStore refreshTokenStore,
            LoginAttemptService loginAttemptService,
            LockoutService lockoutService,
            AuditLogService auditLogService,
            JwtUtils jwtUtils,
            PasswordHasher passwordHasher,
            @Qualifier("authAsync") Executor asyncExecutor,
            PlatformTransactionManager txManager,
            TenantSlugResolver tenantSlugResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher,
            @Autowired(required = false) MfaLoginGate mfaLoginGate,
            UserDisplayNameResolver userDisplayNameResolver) {
        this.sessionRepo            = sessionRepo;
        this.refreshTokenAuditRepo  = refreshTokenAuditRepo;
        this.authUserRoleRepository = authUserRoleRepository;
        this.refreshTokenStore      = refreshTokenStore;
        this.loginAttemptService    = loginAttemptService;
        this.lockoutService         = lockoutService;
        this.auditLogService        = auditLogService;
        this.jwtUtils               = jwtUtils;
        this.passwordHasher         = passwordHasher;
        this.asyncExecutor          = asyncExecutor;
        this.txTemplate             = new TransactionTemplate(txManager);
        this.tenantSlugResolver     = tenantSlugResolver;
        this.authEventPublisher     = authEventPublisher;
        this.mfaLoginGate           = mfaLoginGate;
        this.userDisplayNameResolver = userDisplayNameResolver;
    }

    @Override
    public LoginResult executeLogin(AuthUserEntity user, LoginRequest request,
                                    String ipAddress, String userAgent, long loginStart) {
        String email = user.getEmail();

        // ── Password verification ──────────────────────────────────────────────
        long t1 = System.currentTimeMillis();
        boolean pwOk = passwordHasher.verify(request.getPassword(), user.getPasswordHash());
        log.info("perf.login.password_verify.ms={}", System.currentTimeMillis() - t1);

        if (!pwOk) {
            log.warn("login.password_invalid userId={} email={} ip={}", user.getId(), email, ipAddress);
            handleFailure(user.getTenantId(), user.getId(), email, ipAddress, userAgent, "INVALID_CREDENTIALS");
            throw new InvalidCredentialsException();
        }

        return proceedPostAuthentication(user, ipAddress, userAgent, loginStart);
    }

    @Override
    public LoginResult proceedPostAuthentication(AuthUserEntity user, String ipAddress, String userAgent, long loginStart) {
        if (user.getUserType() == UserType.TENANT_USER && mfaLoginGate != null) {
            var challenge = mfaLoginGate.checkAndIssueChallenge(user, ipAddress, userAgent);
            if (challenge.isPresent()) {
                return challenge.get();
            }
        }
        return issueTokens(user, buildSyntheticLoginRequest(user), ipAddress, userAgent, loginStart);
    }

    private LoginRequest buildSyntheticLoginRequest(AuthUserEntity user) {
        // issueTokens(...) reads request.getEmail() nowhere in its body — a bare
        // LoginRequest carrying just the email keeps the method signature
        // unchanged without requiring a second overload. Same trick
        // MfaController.buildSyntheticLoginRequest already uses.
        LoginRequest synthetic = new LoginRequest();
        synthetic.setEmail(user.getEmail());
        return synthetic;
    }

    @Override
    public LoginResult issueTokens(AuthUserEntity user, LoginRequest request,
                                   String ipAddress, String userAgent, long loginStart) {
        String email = user.getEmail();

        // ── Load roles from auth_user_roles (Phase 4) ─────────────────────────
        // auth_user_roles is the authoritative multi-role read model for TENANT_USER.
        // SUPER_ADMIN / SUPER_ADMIN_IMPERSONATING users bypass this check — they
        // do not have auth_user_roles entries and use a separate permission model.
        List<UUID> roleIds;
        if (user.getUserType() == UserType.SUPER_ADMIN
                || user.getUserType() == UserType.SUPER_ADMIN_IMPERSONATING) {
            roleIds = List.of();  // super admins have no ADM roles; JWT carries no role_ids[]
        } else {
            roleIds = authUserRoleRepository.findRoleIdsByUserId(user.getId());
            if (roleIds.isEmpty()) {
                // This service owns no Role/Permission concept of its own — an empty
                // auth_user_roles set is expected for a freshly self-registered TENANT_USER
                // (there is no external consumer left that backfills role assignments).
                // Log in with an empty role_ids[] claim; authorization on top of that is
                // left to whatever application consumes this JWT.
                log.info("login.no_roles userId={} tenantId={} — proceeding with empty role_ids[]",
                        user.getId(), user.getTenantId());
            }
        }

        UUID    sessionId = UUID.randomUUID();
        Instant now       = Instant.now();

        String deviceFingerprint = RefreshTokenHashUtil.hash(
                userAgent != null ? userAgent : "unknown");

        // ── Session persistence — minimal TX scope ─────────────────────────────
        long t2 = System.currentTimeMillis();
        txTemplate.executeWithoutResult(status -> {
            long tConn = System.currentTimeMillis();
            log.info("perf.login.tx_conn_acquired.ms={}", tConn - t2);
            sessionRepo.save(AuthSessionMapper.toEntity(
                    user, roleIds, sessionId, ipAddress, userAgent, deviceFingerprint, now));
            log.info("perf.login.session_persist.ms={}", System.currentTimeMillis() - tConn);
        });
        log.info("perf.login.session_save_total.ms={}", System.currentTimeMillis() - t2);

        // ── JWT generation — uses role_ids[] from auth_user_roles ─────────────
        String tenantSlug = tenantSlugResolver.resolve(user.getTenantId());
        String tenantName = tenantSlugResolver.resolveName(user.getTenantId());
        String username    = userDisplayNameResolver.resolve(user.getId());
        JwtClaims claims = new JwtClaims(
                user.getId(), user.getTenantId(), tenantSlug,
                roleIds, user.getUserType(),
                now, now.plus(15, ChronoUnit.MINUTES), sessionId.toString(), null,
                username, email, tenantName);

        long t3 = System.currentTimeMillis();
        TokenPair tokenPair = jwtUtils.generateTokenPair(claims);
        log.info("perf.login.jwt_generate.ms={}", System.currentTimeMillis() - t3);

        // ── Redis refresh-token store ──────────────────────────────────────────
        UUID   familyId  = UUID.randomUUID();
        String tokenHash = RefreshTokenHashUtil.hash(tokenPair.refreshToken());

        long t4 = System.currentTimeMillis();
        refreshTokenStore.save(RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .sessionId(sessionId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .generation(0)
                .deviceFingerprint(deviceFingerprint)
                .used(false)
                .expiresAt(tokenPair.refreshTokenExpiry())
                .absoluteExpiresAt(tokenPair.refreshTokenExpiry())
                .createdAt(now)
                .build());
        log.info("perf.login.redis_store.ms={}", System.currentTimeMillis() - t4);
        log.info("session.absolute_expiry sessionId={} absoluteExpiresAt={} familyId={}",
                sessionId, tokenPair.refreshTokenExpiry(), familyId);

        // ── Critical path complete ─────────────────────────────────────────────
        long criticalMs = System.currentTimeMillis() - loginStart;
        log.info("login.success email={} userId={} tenantId={} sessionId={} roleCount={} " +
                 "perf.login.critical_path.ms={}",
                email, user.getId(), user.getTenantId(), sessionId, roleIds.size(), criticalMs);

        lockoutService.clearFailure(email, ipAddress);

        // ── Async side-effects ─────────────────────────────────────────────────
        final UUID     fUserId    = user.getId();
        final UUID     fTenantId  = user.getTenantId();
        final UUID     fSessionId = sessionId;
        final UUID     fFamilyId  = familyId;
        final String   fTokenHash = tokenHash;
        final Instant  fExpiry    = tokenPair.refreshTokenExpiry();
        final String   fIp        = ipAddress;
        final String   fUa        = userAgent;
        final UserType fUserType  = user.getUserType();

        loginAttemptService.record(new LoginAttemptRequest(
                fTenantId, fUserId, email, fIp, fUa, true, null));
        auditLogService.log(new AuditLogRequest(
                fTenantId, fUserId, "LOGIN_SUCCESS", fIp, fUa, null));

        asyncExecutor.execute(() -> {
            if (authEventPublisher != null) {
                authEventPublisher.publishLoginSuccess(fUserId, fTenantId, fSessionId, fIp, fUa);
                if (fUserType == UserType.SUPER_ADMIN) {
                    authEventPublisher.publishAuditPlatformLoginSuccess(fUserId, Instant.now());
                } else {
                    authEventPublisher.publishAuditTenantLoginSuccess(fUserId, fTenantId, Instant.now());
                }
            }
        });

        asyncExecutor.execute(() -> {
            try {
                refreshTokenAuditRepo.save(AuthRefreshTokenMapper.toEntity(
                        fUserId, fTenantId, fSessionId, fTokenHash, fFamilyId, fExpiry));
            } catch (Exception e) {
                log.error("async.refresh_token_archive_failed sessionId={} reason={}",
                        fSessionId, e.getMessage(), e);
            }
        });

        log.info("perf.login.total.ms={} sessionId={}", System.currentTimeMillis() - loginStart, sessionId);

        // accessToken is included in the response body so API clients (Swagger, Postman, mobile)
        // can use it as a Bearer token. Browsers use the HttpOnly cookie set by AuthController.
        var response = LoginResponseMapper.toResponse(user, tokenPair.accessTokenExpiry(),
                tokenPair.accessToken());
        return LoginResponseMapper.toResult(
                response, tokenPair.accessToken(), tokenPair.refreshToken(),
                Duration.between(now, tokenPair.accessTokenExpiry()),
                Duration.between(now, tokenPair.refreshTokenExpiry()));
    }

    @Override
    public void handleFailure(UUID tenantId, UUID userId, String email,
                              String ip, String userAgent, String reason) {
        log.warn("login.failed email={} ip={} reason={}", email, ip, reason);

        lockoutService.recordFailure(email, ip);

        loginAttemptService.record(new LoginAttemptRequest(
                tenantId, userId, email, ip, userAgent, false, reason));
        auditLogService.log(new AuditLogRequest(
                tenantId, userId, "LOGIN_FAILED", ip, userAgent, reason));

        asyncExecutor.execute(() -> {
            if (authEventPublisher != null) {
                authEventPublisher.publishLoginFailed(email, ip, reason);
                // No resolved user/UserType is available here (the account may not even
                // exist for an unknown-email attempt) — tenantId is the only signal we
                // have. This is imprecise for single-tenant deployments and unknown-email
                // attempts (see TenantConstants.PLATFORM_TENANT_ID's own Javadoc — it
                // covers both super-admins AND single-tenant deployments), but it's the
                // best available answer without an extra user lookup on the failure path.
                if (TenantConstants.PLATFORM_TENANT_ID.equals(tenantId)) {
                    authEventPublisher.publishAuditPlatformLoginFailed(Instant.now(), reason);
                } else {
                    authEventPublisher.publishAuditTenantLoginFailed(tenantId, Instant.now(), reason);
                }
            }
        });
    }
}
