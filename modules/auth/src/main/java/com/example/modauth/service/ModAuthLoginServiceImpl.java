package com.example.modauth.service;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.AccountLockedException;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.modauth.domain.Role;
import com.example.modauth.dto.ModLoginRequest;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Implements the "Logging in" flow diagram end to end by composing
 * gen-auth-starter's own building blocks in the exact decision order the
 * diagram calls for — lockout, then credentials, then account-active,
 * then terms, then role — rather than re-implementing password hashing,
 * lockout counters or JWT issuance.
 *
 * <p>{@link LoginExecutionService#issueTokens} (not {@code executeLogin}) is
 * used deliberately: this class has already verified the password itself by
 * the time it's called, so it skips straight to session/JWT issuance instead
 * of verifying the password a second time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModAuthLoginServiceImpl implements ModAuthLoginService {

    private final ModAuthUserLookupRepository userLookupRepo;
    private final ModAuthUserRoleJpaRepository roleRepo;
    private final LockoutService lockoutService;
    private final PasswordHasher passwordHasher;
    private final LoginExecutionService loginExecutor;
    private final TenantAccessService tenantAccessService;
    private final PlatformEventClient platformEvents;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${modauth.terms.current-version:1}")
    private int currentTermsVersion;

    @Override
    public ModAuthLoginResult login(ModLoginRequest request, String ipAddress, String userAgent) {
        String email = request.email().toLowerCase().strip();
        long loginStart = System.currentTimeMillis();

        // ── 1. Lockout guard ───────────────────────────────────────────────
        lockoutService.checkLockout(email, ipAddress);

        // ── 2. Email + password correct? ────────────────────────────────────
        Optional<AuthUserEntity> userOpt = userLookupRepo.findByEmail(email);
        boolean passwordOk = userOpt.isPresent()
                && userOpt.get().getPasswordHash() != null
                && passwordHasher.verify(request.password(), userOpt.get().getPasswordHash());

        if (!passwordOk) {
            // handleFailure already calls lockoutService.recordFailure internally
            // (plus records the login-attempt audit row and publishes the failed-
            // login event) — calling recordFailure here too double-counted every
            // failure, tripping the lock after 3 attempts instead of 5. Confirmed
            // by an actual test: attempt 3/5 already returned 429.
            loginExecutor.handleFailure(
                    userOpt.map(AuthUserEntity::getTenantId).orElse(null),
                    userOpt.map(AuthUserEntity::getId).orElse(null),
                    email, ipAddress, userAgent, "INVALID_CREDENTIALS");
            log.warn("modauth.login.invalid_credentials email={} ip={}", email, ipAddress);
            warnIfJustLockedOut(email, ipAddress, userOpt.orElse(null));
            throw new InvalidCredentialsException();
        }

        AuthUserEntity user = userOpt.get();

        // ── 3. Is the account active? ───────────────────────────────────────
        if (!user.isActive()) {
            log.info("modauth.login.deactivated userId={}", user.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account deactivated. Contact your admin.");
        }

        // ── 3b. Is their brokerage suspended or cancelled? (Flow 1) ─────────
        if (tenantAccessService.isBlocked(user.getTenantId())) {
            log.info("modauth.login.tenant_blocked userId={} tenantId={}", user.getId(), user.getTenantId());
            platformEvents.send("security.login_blocked_suspended_brokerage", user.getTenantId(), user.getId(),
                    ipAddress, null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your brokerage's access is suspended. Contact your administrator.");
        }

        lockoutService.clearFailure(email, ipAddress);

        Optional<ModAuthUserRoleEntity> roleRow = roleRepo.findById(user.getId());

        // ── 4. Have the terms changed since last login? ────────────────────
        int acceptedVersion = roleRow.map(ModAuthUserRoleEntity::getAcceptedTermsVersion).orElse(0);
        if (acceptedVersion < currentTermsVersion) {
            if (!request.acceptTerms()) {
                log.info("modauth.login.terms_gate userId={} accepted={} current={}",
                        user.getId(), acceptedVersion, currentTermsVersion);
                return ModAuthLoginResult.termsGate(currentTermsVersion);
            }
            roleRow.ifPresent(row -> {
                row.setAcceptedTermsVersion(currentTermsVersion);
                roleRepo.save(row);
            });
        }

        // ── 5. Issue tokens — credentials and account status already verified ──
        LoginRequest starterRequest = new LoginRequest();
        starterRequest.setEmail(email);
        starterRequest.setPassword(request.password());
        LoginResult result = loginExecutor.issueTokens(user, starterRequest, ipAddress, userAgent, loginStart);

        // ── 6. Which role? → dashboard hint ─────────────────────────────────
        Role role = roleRow.map(ModAuthUserRoleEntity::getRole)
                .orElse(user.getUserType() == UserType.SUPER_ADMIN ? Role.SUPER_ADMIN : null);
        String dashboard = role == null ? null : role.dashboardKey();

        log.info("modauth.login.success userId={} role={}", user.getId(), role);
        return ModAuthLoginResult.success(role, dashboard, result);
    }

    /**
     * checkLockout() at the top of login() already proved this email/ip
     * wasn't locked before this attempt — calling it again right after
     * handleFailure() (which internally calls recordFailure()) is a
     * side-effect-free way to tell "just got locked by this attempt" from
     * "still has failures left", without gen-auth-starter needing to
     * expose a new read-only check or change recordFailure's return type.
     */
    private void warnIfJustLockedOut(String email, String ipAddress, AuthUserEntity user) {
        try {
            lockoutService.checkLockout(email, ipAddress);
        } catch (AccountLockedException e) {
            sendLockoutWarningEmail(email);
            platformEvents.send("security.account_locked",
                    user == null ? null : user.getTenantId(), user == null ? null : user.getId(), ipAddress,
                    "Locked for 15 minutes after 5 failed attempts");
        }
    }

    private void sendLockoutWarningEmail(String email) {
        // ponytail: plain JavaMailSender, same as InvitationServiceImpl's
        // invitation email — upgrade to gen-auth-starter's EmailProvider
        // pattern if this needs to match the rest of the product's styling.
        if (mailSender == null) {
            log.info("modauth.login.lockout_email_skipped_no_mail_sender email={}", email);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setTo(email);
            helper.setSubject("Your account was temporarily locked");
            helper.setText("We locked your account for 15 minutes after 5 failed login attempts.\n\n"
                    + "If this wasn't you, consider changing your password once you're back in.");
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("modauth.login.lockout_email_send_failed email={}", email, e);
        }
    }
}
