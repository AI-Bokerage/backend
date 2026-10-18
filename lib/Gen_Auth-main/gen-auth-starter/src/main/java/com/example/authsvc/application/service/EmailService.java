package com.example.authsvc.application.service;

/**
 * Minimal abstraction for sending transactional emails.
 * Only the subset needed by the magic-link password-reset flow is declared here.
 */
public interface EmailService {

    /**
     * Send a password-reset magic link to the given recipient.
     *
     * @param toEmail   recipient email address
     * @param resetUrl  full reset URL including the raw token as a query parameter
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
