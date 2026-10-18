package com.example.authsvc.infrastructure.email.provider;

import com.example.authsvc.infrastructure.email.template.EmailHtmlTemplate;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * {@link EmailProvider} that delivers transactional email via SMTP using
 * Spring's {@link JavaMailSender}.
 *
 * <p>Active when {@code mail.provider=smtp} (the default).
 * Created as a Spring bean by
 * {@link com.example.authsvc.infrastructure.email.config.EmailProviderConfig};
 * not registered as a {@code @Component} directly so that the config class
 * can conditionally instantiate either this or {@link SesEmailProvider}.
 *
 * <p>Sent as a multipart/alternative message: an HTML part built via
 * {@link EmailHtmlTemplate} plus a plain-text fallback for clients that
 * don't render HTML.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailProvider implements EmailProvider {

    static final String PASSWORD_RESET_SUBJECT = "Reset your password";
    static final String SUPER_ADMIN_BOOTSTRAP_SUBJECT = "Your super admin account is ready";
    static final String OTP_SUBJECT = "Your verification code";
    static final String TENANT_ADMIN_WELCOME_SUBJECT = "Your admin account is ready";
    static final String INVITATION_WELCOME_SUBJECT = "Your account is ready";

    private final JavaMailSender mailSender;

    @Override
    public void sendPasswordResetLink(String toEmail, String resetUrl) {
        log.info("email.send.started provider=smtp type=password_reset to=***");
        try {
            send(toEmail, PASSWORD_RESET_SUBJECT, buildResetHtml(resetUrl), buildBody(resetUrl));
            log.info("email.send.success provider=smtp type=password_reset");
        } catch (Exception e) {
            log.error("email.send.failure provider=smtp type=password_reset reason={}", e.getMessage());
            // Intentionally swallowed: the caller already returned 202.
        }
    }

    private String buildResetHtml(String resetUrl) {
        return EmailHtmlTemplate.render(
                "Security",
                "Reset your password",
                null,
                "You requested a password reset for your account. Click the button below to set a new password. This link is valid for 15 minutes.",
                null,
                "Reset Password",
                resetUrl,
                "If you did not request this, you can safely ignore this email. Your password will not be changed until you click the button above.");
    }

    private String buildBody(String resetUrl) {
        return """
                You requested a password reset for your account.

                Click the link below to set a new password (valid for 15 minutes):

                %s

                If you did not request this, you can safely ignore this email.
                Your password will not be changed until you click the link above.
                """.formatted(resetUrl);
    }

    @Override
    public void sendSuperAdminBootstrapCredentials(String email, String password) {
        log.info("email.send.started provider=smtp type=superadmin_bootstrap to=***");
        try {
            send(email, SUPER_ADMIN_BOOTSTRAP_SUBJECT,
                    buildBootstrapHtml(email, password), buildBootstrapBody(email, password));
            log.info("email.send.success provider=smtp type=superadmin_bootstrap");
        } catch (Exception e) {
            log.error("email.send.failure provider=smtp type=superadmin_bootstrap reason={}", e.getMessage());
        }
    }

    private String buildBootstrapHtml(String email, String password) {
        return EmailHtmlTemplate.render(
                "Account",
                "Your super admin account is ready",
                null,
                "A platform super admin account has been created. Use the credentials below to log in.",
                java.util.List.of(new EmailHtmlTemplate.InfoRow("Email", email),
                        new EmailHtmlTemplate.InfoRow("Temporary Password", password)),
                null,
                null,
                "Please log in and change your password immediately. This email is sent only when the super admin account is created or its email is reassigned.");
    }

    private String buildBootstrapBody(String email, String password) {
        return """
                A platform super admin account is ready.

                Email: %s

                Temporary Password: %s

                Please log in and change your password immediately.

                This email is sent only when the super admin account is created or its email is reassigned.
                """.formatted(email, password);
    }

    @Override
    public void sendOtpCode(String toEmail, String code, String purpose) {
        log.info("email.send.started provider=smtp type=otp to=***");
        try {
            send(toEmail, OTP_SUBJECT, buildOtpHtml(code, purpose), buildOtpBody(code, purpose));
            log.info("email.send.success provider=smtp type=otp");
        } catch (Exception e) {
            log.error("email.send.failure provider=smtp type=otp reason={}", e.getMessage());
        }
    }

    private String buildOtpHtml(String code, String purpose) {
        return EmailHtmlTemplate.render(
                "Security",
                "Your verification code",
                null,
                "Use the code below to complete: " + purpose + ". This code is valid for a few minutes.",
                java.util.List.of(new EmailHtmlTemplate.InfoRow("Code", code)),
                null,
                null,
                "If you did not request this code, you can safely ignore this email.");
    }

    private String buildOtpBody(String code, String purpose) {
        return """
                Use the code below to complete: %s

                Code: %s

                This code is valid for a few minutes. If you did not request it, you can safely ignore this email.
                """.formatted(purpose, code);
    }

    @Override
    public void sendTenantAdminWelcome(String toEmail, String firstName, String password) {
        log.info("email.send.started provider=smtp type=tenant_admin_welcome to=***");
        try {
            send(toEmail, TENANT_ADMIN_WELCOME_SUBJECT,
                    buildWelcomeHtml(firstName, toEmail, password), buildWelcomeBody(firstName, toEmail, password));
            log.info("email.send.success provider=smtp type=tenant_admin_welcome");
        } catch (Exception e) {
            log.error("email.send.failure provider=smtp type=tenant_admin_welcome reason={}", e.getMessage());
        }
    }

    private String buildWelcomeHtml(String firstName, String email, String password) {
        return EmailHtmlTemplate.render(
                "Welcome",
                "Your admin account is ready",
                "Hello " + firstName + ",",
                "Your tenant admin account has been created and is ready to use.",
                java.util.List.of(new EmailHtmlTemplate.InfoRow("Email", email),
                        new EmailHtmlTemplate.InfoRow("Temporary Password", password)),
                null,
                null,
                "Please log in and change your password on first login.");
    }

    private String buildWelcomeBody(String firstName, String email, String password) {
        return """
                Hi %s,

                Your tenant admin account has been created and is ready to use.

                Email: %s
                Temporary Password: %s

                Please log in and change your password on first login.
                """.formatted(firstName, email, password);
    }

    @Override
    public void sendInvitationWelcome(String toEmail, String firstName, String password, String loginUrl) {
        log.info("email.send.started provider=smtp type=invitation_welcome to=***");
        try {
            send(toEmail, INVITATION_WELCOME_SUBJECT,
                    buildInvitationWelcomeHtml(firstName, toEmail, password, loginUrl),
                    buildInvitationWelcomeBody(firstName, toEmail, password, loginUrl));
            log.info("email.send.success provider=smtp type=invitation_welcome");
        } catch (Exception e) {
            log.error("email.send.failure provider=smtp type=invitation_welcome reason={}", e.getMessage());
        }
    }

    private String buildInvitationWelcomeHtml(String firstName, String email, String password, String loginUrl) {
        return EmailHtmlTemplate.render(
                "Invitation",
                "Your account is ready",
                "Hello " + firstName + ",",
                "Your account has been created. You can now log in using the credentials below.",
                java.util.List.of(new EmailHtmlTemplate.InfoRow("Email", email),
                        new EmailHtmlTemplate.InfoRow("Password", password)),
                "Log In",
                loginUrl,
                "Please change your password after your first login.");
    }

    private String buildInvitationWelcomeBody(String firstName, String email, String password, String loginUrl) {
        return """
                Hi %s,

                Your account has been created. You can now log in using the credentials below.

                Login URL: %s
                Email: %s
                Password: %s

                Please change your password after your first login.
                """.formatted(firstName, loginUrl, email, password);
    }

    private void send(String toEmail, String subject, String htmlBody, String textBody) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(textBody, htmlBody);
        mailSender.send(mimeMessage);
    }
}
