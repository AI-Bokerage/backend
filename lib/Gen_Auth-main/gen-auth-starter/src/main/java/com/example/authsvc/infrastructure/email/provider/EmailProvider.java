package com.example.authsvc.infrastructure.email.provider;

/**
 * Low-level email delivery abstraction.
 *
 * <p>Implementations handle the transport details for a specific backend
 * (SMTP, AWS SES) without carrying any business logic. The active
 * implementation is selected at startup based on {@code mail.provider}:
 *
 * <ul>
 *   <li>{@code smtp} (default) — {@link SmtpEmailProvider}</li>
 *   <li>{@code ses}            — {@link SesEmailProvider}</li>
 * </ul>
 *
 * <p>Business services depend only on {@link com.example.authsvc.application.service.EmailService};
 * they never interact with an {@code EmailProvider} directly.
 */
public interface EmailProvider {

    /**
     * Send a password-reset magic link to the given recipient.
     *
     * @param toEmail   recipient email address
     * @param resetUrl  full reset URL including the raw token
     */
    void sendPasswordResetLink(String toEmail, String resetUrl);

    /**
     * Send the bootstrap credentials for the platform super admin.
     *
     * @param email     recipient email address
     * @param password  plaintext bootstrap password from configuration
     */
    void sendSuperAdminBootstrapCredentials(String email, String password);

    /**
     * Send a one-time-passcode to the given recipient.
     *
     * @param toEmail recipient email address
     * @param code    the plaintext OTP code
     * @param purpose caller-supplied label for what the code is for (e.g. "login", "withdrawal")
     */
    void sendOtpCode(String toEmail, String code, String purpose);

    /**
     * Send a welcome email to a newly provisioned tenant admin with their temporary credentials.
     *
     * @param toEmail   recipient email address
     * @param firstName recipient first name
     * @param password  plaintext temporary password
     */
    void sendTenantAdminWelcome(String toEmail, String firstName, String password);

    /**
     * Send a welcome email to a user provisioned through an invitation flow, including a
     * temporary password and a direct login URL.
     *
     * @param toEmail   recipient email address
     * @param firstName recipient first name
     * @param password  plaintext temporary password
     * @param loginUrl  frontend login URL
     */
    void sendInvitationWelcome(String toEmail, String firstName, String password, String loginUrl);
}
