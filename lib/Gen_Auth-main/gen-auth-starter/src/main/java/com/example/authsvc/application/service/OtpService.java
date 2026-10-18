package com.example.authsvc.application.service;

import java.util.UUID;

public interface OtpService {

    /** Generates a code, stores its hash, emails the plaintext code, returns the new otpId. */
    UUID requestOtp(String toEmail, String purpose);

    /** Hashes the supplied code and atomically checks-and-consumes it against the stored entry. */
    boolean verifyOtp(UUID otpId, String code);
}
