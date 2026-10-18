package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.application.service.OtpService;
import com.example.authsvc.domain.port.OtpStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * Issues and verifies caller-supplied-email one-time codes for
 * {@code POST /internal/otp/request} and {@code POST /internal/otp/verify}.
 * The plaintext code is never persisted — only its SHA-256 hash, via {@link OtpStore}.
 * Gated by {@code app.otp.enabled=true}.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.otp", name = "enabled", havingValue = "true")
public class OtpServiceImpl implements OtpService {

    private final OtpStore     otpStore;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.expiration-minutes:5}")
    private int ttlMinutes;

    public OtpServiceImpl(OtpStore otpStore, EmailService emailService) {
        this.otpStore = otpStore;
        this.emailService = emailService;
    }

    @Override
    public UUID requestOtp(String toEmail, String purpose) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        String hash = sha256Hex(code);

        UUID otpId = otpStore.issue(hash, Duration.ofMinutes(ttlMinutes));
        emailService.sendOtpCode(toEmail, code, purpose);

        log.info("otp.requested otpId={} purpose={}", otpId, purpose);
        return otpId;
    }

    @Override
    public boolean verifyOtp(UUID otpId, String code) {
        boolean verified = otpStore.verifyAndConsume(otpId, sha256Hex(code));
        log.info("otp.verify.attempted otpId={} verified={}", otpId, verified);
        return verified;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
