package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.MfaService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.model.MfaEnrollment;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserMfaEntity;
import com.example.authsvc.infrastructure.persistence.entity.MfaBackupCodeEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserMfaJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.MfaBackupCodeJpaRepository;
import com.example.authsvc.infrastructure.security.mfa.Base32Codec;
import com.example.authsvc.infrastructure.security.mfa.MfaSecretEncryptionUtil;
import com.example.authsvc.infrastructure.security.mfa.TotpGenerator;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TOTP MFA enrollment, confirmation, disable, and verification (with
 * single-use backup-code fallback). Only registered when
 * {@code app.mfa.enabled=true} — this class hard-depends on
 * {@link MfaSecretEncryptionUtil}, which is itself only registered when
 * enabled (see Task 9's {@code MfaConfig}), so this bean must be gated the
 * same way or boot fails with an unsatisfied dependency whenever MFA is off
 * (the default) — the same class of bug the RabbitMQ event-publishing slice
 * hit and fixed earlier in this project.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.mfa", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private static final int SECRET_BYTES = 20; // 160 bits, standard TOTP secret length
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_BYTES = 10; // hex-encoded to a 20-char code

    private final AuthUserMfaJpaRepository authUserMfaRepo;
    private final MfaBackupCodeJpaRepository backupCodeRepo;
    private final MfaSecretEncryptionUtil encryptionUtil;
    private final SecureRandom random = new SecureRandom();

    @Override
    public MfaEnrollment enroll(UUID userId, String email) {
        byte[] rawSecret = new byte[SECRET_BYTES];
        random.nextBytes(rawSecret);

        AuthUserMfaEntity entity = authUserMfaRepo.findByUserId(userId)
                .orElseGet(() -> AuthUserMfaEntity.builder().userId(userId).build());
        entity.setTotpSecretCiphertext(encryptionUtil.encrypt(rawSecret));
        entity.setEnabled(false);
        authUserMfaRepo.save(entity);

        String base32Secret = Base32Codec.encode(rawSecret);
        String otpauthUri = "otpauth://totp/GenAuth:" + urlEncode(email)
                + "?secret=" + base32Secret + "&issuer=GenAuth";

        log.info("mfa.enroll.started userId={}", userId);
        return new MfaEnrollment(otpauthUri, base32Secret);
    }

    @Override
    @Transactional
    public List<String> confirmEnrollment(UUID userId, String code) {
        AuthUserMfaEntity entity = authUserMfaRepo.findByUserId(userId)
                .orElseThrow(UnauthorizedException::new);
        byte[] rawSecret = encryptionUtil.decrypt(entity.getTotpSecretCiphertext());

        if (!matchesTotpWithTolerance(rawSecret, code)) {
            throw new UnauthorizedException();
        }

        entity.setEnabled(true);
        entity.setEnrolledAt(Instant.now());
        authUserMfaRepo.save(entity);

        backupCodeRepo.deleteByUserId(userId);
        List<String> plaintextCodes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            byte[] codeBytes = new byte[BACKUP_CODE_BYTES];
            random.nextBytes(codeBytes);
            String plaintext = java.util.HexFormat.of().formatHex(codeBytes).toUpperCase();
            plaintextCodes.add(plaintext);

            MfaBackupCodeEntity backupCode = MfaBackupCodeEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .codeHash(RefreshTokenHashUtil.hash(plaintext))
                    .build();
            backupCodeRepo.save(backupCode);
        }

        log.info("mfa.enroll.confirmed userId={}", userId);
        return plaintextCodes;
    }

    @Override
    @Transactional
    public void disable(UUID userId) {
        authUserMfaRepo.findByUserId(userId).ifPresent(entity -> authUserMfaRepo.deleteById(userId));
        backupCodeRepo.deleteByUserId(userId);
        log.info("mfa.disabled userId={}", userId);
    }

    @Override
    public boolean verifyCode(UUID userId, String code) {
        Optional<AuthUserMfaEntity> entityOpt = authUserMfaRepo.findByUserId(userId);
        if (entityOpt.isPresent() && entityOpt.get().isEnabled()) {
            byte[] rawSecret = encryptionUtil.decrypt(entityOpt.get().getTotpSecretCiphertext());
            if (matchesTotpWithTolerance(rawSecret, code)) {
                return true;
            }
        }
        return tryBackupCode(userId, code);
    }

    @Override
    public boolean isEnrolled(UUID userId) {
        return authUserMfaRepo.findByUserId(userId)
                .map(AuthUserMfaEntity::isEnabled)
                .orElse(false);
    }

    /** ±1 time-step (30s) tolerance window for clock drift, per the design spec. */
    private boolean matchesTotpWithTolerance(byte[] rawSecret, String code) {
        Instant now = Instant.now();
        for (int stepOffset = -1; stepOffset <= 1; stepOffset++) {
            Instant shifted = now.plusSeconds(30L * stepOffset);
            if (TotpGenerator.generate(rawSecret, shifted).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryBackupCode(UUID userId, String code) {
        String hash = RefreshTokenHashUtil.hash(code);
        for (MfaBackupCodeEntity backupCode : backupCodeRepo.findByUserIdAndUsedAtIsNull(userId)) {
            if (backupCode.getCodeHash().equals(hash)) {
                backupCode.setUsedAt(Instant.now());
                backupCodeRepo.save(backupCode);
                log.info("mfa.backup_code.redeemed userId={}", userId);
                return true;
            }
        }
        return false;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
