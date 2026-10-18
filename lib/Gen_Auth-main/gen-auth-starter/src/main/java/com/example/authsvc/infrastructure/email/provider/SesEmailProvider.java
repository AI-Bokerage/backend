package com.example.authsvc.infrastructure.email.provider;

import com.example.authsvc.infrastructure.email.template.EmailHtmlTemplate;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * {@link EmailProvider} that delivers transactional email via AWS Simple Email Service.
 *
 * <p>Active when {@code mail.provider=ses}. Created as a Spring bean by
 * {@link com.example.authsvc.infrastructure.email.config.EmailProviderConfig}.
 *
 * <p>Credentials are resolved through the AWS SDK v2 default provider chain
 * (environment variables → EC2 instance profile → ECS task role).
 * Explicit credentials can optionally be supplied via
 * {@code aws.access-key-id} / {@code aws.secret-access-key} properties.
 */
@Slf4j
public class SesEmailProvider implements EmailProvider {

    private static final String PASSWORD_RESET_SUBJECT = SmtpEmailProvider.PASSWORD_RESET_SUBJECT;
    private static final String SUPER_ADMIN_BOOTSTRAP_SUBJECT = SmtpEmailProvider.SUPER_ADMIN_BOOTSTRAP_SUBJECT;
    private static final String OTP_SUBJECT = SmtpEmailProvider.OTP_SUBJECT;
    private static final String TENANT_ADMIN_WELCOME_SUBJECT = SmtpEmailProvider.TENANT_ADMIN_WELCOME_SUBJECT;
    private static final String INVITATION_WELCOME_SUBJECT = SmtpEmailProvider.INVITATION_WELCOME_SUBJECT;

    private final SesClient sesClient;
    private final String    fromAddress;

    public SesEmailProvider(SesClient sesClient, String fromAddress) {
        this.sesClient   = sesClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordResetLink(String toEmail, String resetUrl) {
        log.info("email.send.started provider=ses type=password_reset to=***");
        try {
            send(toEmail, PASSWORD_RESET_SUBJECT, buildResetHtml(resetUrl), buildBody(resetUrl));
            log.info("email.send.success provider=ses type=password_reset");
        } catch (SesException e) {
            log.error("email.send.failure provider=ses type=password_reset reason={} awsError={}",
                    e.getMessage(), e.awsErrorDetails().errorCode());
            // Intentionally swallowed: the caller already returned 202.
        } catch (Exception e) {
            log.error("email.send.failure provider=ses type=password_reset reason={}", e.getMessage());
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
        log.info("email.send.started provider=ses type=superadmin_bootstrap to=***");
        try {
            send(email, SUPER_ADMIN_BOOTSTRAP_SUBJECT,
                    buildBootstrapHtml(email, password), buildBootstrapBody(email, password));
            log.info("email.send.success provider=ses type=superadmin_bootstrap");
        } catch (SesException e) {
            log.error("email.send.failure provider=ses type=superadmin_bootstrap reason={} awsError={}",
                    e.getMessage(), e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            log.error("email.send.failure provider=ses type=superadmin_bootstrap reason={}", e.getMessage());
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
        log.info("email.send.started provider=ses type=otp to=***");
        try {
            send(toEmail, OTP_SUBJECT, buildOtpHtml(code, purpose), buildOtpBody(code, purpose));
            log.info("email.send.success provider=ses type=otp");
        } catch (SesException e) {
            log.error("email.send.failure provider=ses type=otp reason={} awsError={}",
                    e.getMessage(), e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            log.error("email.send.failure provider=ses type=otp reason={}", e.getMessage());
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
        log.info("email.send.started provider=ses type=tenant_admin_welcome to=***");
        try {
            send(toEmail, TENANT_ADMIN_WELCOME_SUBJECT,
                    buildWelcomeHtml(firstName, toEmail, password), buildWelcomeBody(firstName, toEmail, password));
            log.info("email.send.success provider=ses type=tenant_admin_welcome");
        } catch (SesException e) {
            log.error("email.send.failure provider=ses type=tenant_admin_welcome reason={} awsError={}",
                    e.getMessage(), e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            log.error("email.send.failure provider=ses type=tenant_admin_welcome reason={}", e.getMessage());
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
        log.info("email.send.started provider=ses type=invitation_welcome to=***");
        try {
            send(toEmail, INVITATION_WELCOME_SUBJECT,
                    buildInvitationWelcomeHtml(firstName, toEmail, password, loginUrl),
                    buildInvitationWelcomeBody(firstName, toEmail, password, loginUrl));
            log.info("email.send.success provider=ses type=invitation_welcome");
        } catch (SesException e) {
            log.error("email.send.failure provider=ses type=invitation_welcome reason={} awsError={}",
                    e.getMessage(), e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            log.error("email.send.failure provider=ses type=invitation_welcome reason={}", e.getMessage());
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

    private void send(String toEmail, String subject, String htmlBody, String textBody) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder().toAddresses(toEmail).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                .text(Content.builder().data(textBody).charset("UTF-8").build())
                                .build())
                        .build())
                .build();
        sesClient.sendEmail(request);
    }
}
