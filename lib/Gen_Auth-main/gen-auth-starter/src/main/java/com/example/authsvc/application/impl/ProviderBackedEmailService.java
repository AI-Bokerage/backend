package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.infrastructure.email.provider.EmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * {@link EmailService} implementation that delegates to the currently active
 * {@link EmailProvider} (SMTP or SES) without containing any transport logic.
 *
 * <p>Owns the {@link Async} boundary: email delivery is fire-and-forget on the
 * {@code authAsync} thread pool. Gated by {@code app.email.enabled=true} — the
 * same condition as {@link com.example.authsvc.infrastructure.email.config.EmailProviderConfig},
 * so this bean and its {@link EmailProvider} dependency are always registered
 * or always absent together.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ProviderBackedEmailService implements EmailService {

    private final EmailProvider emailProvider;

    @Override
    @Async("authAsync")
    public void sendPasswordResetLink(String toEmail, String resetUrl) {
        emailProvider.sendPasswordResetLink(toEmail, resetUrl);
    }

    @Override
    @Async("authAsync")
    public void sendSuperAdminBootstrapCredentials(String email, String password) {
        try {
            emailProvider.sendSuperAdminBootstrapCredentials(email, password);
            log.info("superadmin.bootstrap.email.sent email={}", email);
        } catch (Exception e) {
            log.error("superadmin.bootstrap.email.failed email={} reason={}", email, e.getMessage(), e);
        }
    }

    @Override
    @Async("authAsync")
    public void sendOtpCode(String toEmail, String code, String purpose) {
        emailProvider.sendOtpCode(toEmail, code, purpose);
    }

    @Override
    @Async("authAsync")
    public void sendTenantAdminWelcome(String toEmail, String firstName, String password) {
        try {
            emailProvider.sendTenantAdminWelcome(toEmail, firstName, password);
            log.info("tenant.admin.welcome.email.sent email=***");
        } catch (Exception e) {
            log.error("tenant.admin.welcome.email.failed reason={}", e.getMessage(), e);
        }
    }

    @Override
    @Async("authAsync")
    public void sendInvitationWelcome(String toEmail, String firstName, String password, String loginUrl) {
        try {
            emailProvider.sendInvitationWelcome(toEmail, firstName, password, loginUrl);
            log.info("invitation.welcome.email.sent email=***");
        } catch (Exception e) {
            log.error("invitation.welcome.email.failed reason={}", e.getMessage(), e);
        }
    }
}
