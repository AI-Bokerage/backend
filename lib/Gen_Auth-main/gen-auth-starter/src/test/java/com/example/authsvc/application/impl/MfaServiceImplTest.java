package com.example.authsvc.application.impl;

import com.example.authsvc.domain.model.MfaEnrollment;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserMfaEntity;
import com.example.authsvc.infrastructure.persistence.entity.MfaBackupCodeEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserMfaJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.MfaBackupCodeJpaRepository;
import com.example.authsvc.infrastructure.security.mfa.MfaSecretEncryptionUtil;
import com.example.authsvc.infrastructure.security.mfa.TotpGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaServiceImplTest {

    @Mock private AuthUserMfaJpaRepository authUserMfaRepo;
    @Mock private MfaBackupCodeJpaRepository backupCodeRepo;

    private MfaSecretEncryptionUtil encryptionUtil;
    private MfaServiceImpl service;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        encryptionUtil = new MfaSecretEncryptionUtil(java.util.Base64.getEncoder().encodeToString(key));
        service = new MfaServiceImpl(authUserMfaRepo, backupCodeRepo, encryptionUtil);
    }

    @Test
    void enroll_newUser_returnsOtpauthUriAndPersistsPendingSecret() {
        UUID userId = UUID.randomUUID();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.empty());

        MfaEnrollment enrollment = service.enroll(userId, "user@example.com");

        assertThat(enrollment.otpauthUri()).startsWith("otpauth://totp/");
        assertThat(enrollment.otpauthUri()).contains("user%40example.com");
        assertThat(enrollment.base32Secret()).isNotBlank();

        ArgumentCaptor<AuthUserMfaEntity> captor = ArgumentCaptor.forClass(AuthUserMfaEntity.class);
        verify(authUserMfaRepo).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(captor.getValue().getTotpSecretCiphertext()).isNotEmpty();
    }

    @Test
    void enroll_calledTwice_overwritesPendingSecret() {
        UUID userId = UUID.randomUUID();
        AuthUserMfaEntity existing = AuthUserMfaEntity.builder()
                .userId(userId).enabled(false)
                .totpSecretCiphertext(encryptionUtil.encrypt("old-secret".getBytes()))
                .build();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(existing));

        service.enroll(userId, "user@example.com");

        verify(authUserMfaRepo).save(any(AuthUserMfaEntity.class));
    }

    @Test
    void confirmEnrollment_validCode_enablesAndReturns10BackupCodes() {
        UUID userId = UUID.randomUUID();
        byte[] rawSecret = "12345678901234567890".getBytes();
        AuthUserMfaEntity pending = AuthUserMfaEntity.builder()
                .userId(userId).enabled(false)
                .totpSecretCiphertext(encryptionUtil.encrypt(rawSecret))
                .build();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(pending));

        String validCode = TotpGenerator.generate(rawSecret, Instant.now());

        List<String> backupCodes = service.confirmEnrollment(userId, validCode);

        assertThat(backupCodes).hasSize(10);
        assertThat(backupCodes).doesNotHaveDuplicates();

        ArgumentCaptor<AuthUserMfaEntity> mfaCaptor = ArgumentCaptor.forClass(AuthUserMfaEntity.class);
        verify(authUserMfaRepo).save(mfaCaptor.capture());
        assertThat(mfaCaptor.getValue().isEnabled()).isTrue();
        assertThat(mfaCaptor.getValue().getEnrolledAt()).isNotNull();

        verify(backupCodeRepo, times(10)).save(any(MfaBackupCodeEntity.class));
    }

    @Test
    void confirmEnrollment_invalidCode_throwsAndDoesNotEnable() {
        UUID userId = UUID.randomUUID();
        byte[] rawSecret = "12345678901234567890".getBytes();
        AuthUserMfaEntity pending = AuthUserMfaEntity.builder()
                .userId(userId).enabled(false)
                .totpSecretCiphertext(encryptionUtil.encrypt(rawSecret))
                .build();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(pending));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.example.authsvc.common.exception.UnauthorizedException.class,
                () -> service.confirmEnrollment(userId, "000000"));

        verify(authUserMfaRepo, never()).save(any());
    }

    @Test
    void disable_removesEntityAndBackupCodes() {
        UUID userId = UUID.randomUUID();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(
                AuthUserMfaEntity.builder().userId(userId).build()));

        service.disable(userId);

        verify(authUserMfaRepo).deleteById(userId);
        verify(backupCodeRepo).deleteByUserId(userId);
    }

    @Test
    void disable_noExistingMfaRow_doesNotThrowAndSkipsDelete() {
        UUID userId = UUID.randomUUID();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.disable(userId));

        verify(authUserMfaRepo, never()).deleteById(any());
        verify(backupCodeRepo).deleteByUserId(userId);
    }

    @Test
    void verifyCode_validTotp_returnsTrue() {
        UUID userId = UUID.randomUUID();
        byte[] rawSecret = "12345678901234567890".getBytes();
        AuthUserMfaEntity entity = AuthUserMfaEntity.builder()
                .userId(userId).enabled(true)
                .totpSecretCiphertext(encryptionUtil.encrypt(rawSecret))
                .build();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(entity));

        String validCode = TotpGenerator.generate(rawSecret, Instant.now());

        assertThat(service.verifyCode(userId, validCode)).isTrue();
    }

    @Test
    void verifyCode_totpFrom30SecondsAgo_returnsTrueWithinToleranceWindow() {
        UUID userId = UUID.randomUUID();
        byte[] rawSecret = "12345678901234567890".getBytes();
        AuthUserMfaEntity entity = AuthUserMfaEntity.builder()
                .userId(userId).enabled(true)
                .totpSecretCiphertext(encryptionUtil.encrypt(rawSecret))
                .build();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(entity));

        String code = TotpGenerator.generate(rawSecret, Instant.now().minusSeconds(30));

        assertThat(service.verifyCode(userId, code)).isTrue();
    }

    @Test
    void verifyCode_totpFrom60SecondsAgo_returnsFalseOutsideToleranceWindow() {
        UUID userId = UUID.randomUUID();
        byte[] rawSecret = "12345678901234567890".getBytes();
        AuthUserMfaEntity entity = AuthUserMfaEntity.builder()
                .userId(userId).enabled(true)
                .totpSecretCiphertext(encryptionUtil.encrypt(rawSecret))
                .build();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(backupCodeRepo.findByUserIdAndUsedAtIsNull(userId)).thenReturn(List.of());

        String code = TotpGenerator.generate(rawSecret, Instant.now().minusSeconds(60));

        assertThat(service.verifyCode(userId, code)).isFalse();
    }

    @Test
    void verifyCode_invalidTotpButValidBackupCode_returnsTrueAndMarksUsed() {
        UUID userId = UUID.randomUUID();
        byte[] rawSecret = "12345678901234567890".getBytes();
        AuthUserMfaEntity entity = AuthUserMfaEntity.builder()
                .userId(userId).enabled(true)
                .totpSecretCiphertext(encryptionUtil.encrypt(rawSecret))
                .build();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(entity));

        String rawBackupCode = "ABCD1234EFGH5678";
        String hash = com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil.hash(rawBackupCode);
        MfaBackupCodeEntity backupCode = MfaBackupCodeEntity.builder()
                .id(UUID.randomUUID()).userId(userId).codeHash(hash).build();
        when(backupCodeRepo.findByUserIdAndUsedAtIsNull(userId)).thenReturn(List.of(backupCode));

        boolean result = service.verifyCode(userId, rawBackupCode);

        assertThat(result).isTrue();
        ArgumentCaptor<MfaBackupCodeEntity> captor = ArgumentCaptor.forClass(MfaBackupCodeEntity.class);
        verify(backupCodeRepo).save(captor.capture());
        assertThat(captor.getValue().getUsedAt()).isNotNull();
    }

    @Test
    void verifyCode_invalidCodeAndNoBackupMatch_returnsFalse() {
        UUID userId = UUID.randomUUID();
        byte[] rawSecret = "12345678901234567890".getBytes();
        AuthUserMfaEntity entity = AuthUserMfaEntity.builder()
                .userId(userId).enabled(true)
                .totpSecretCiphertext(encryptionUtil.encrypt(rawSecret))
                .build();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(backupCodeRepo.findByUserIdAndUsedAtIsNull(userId)).thenReturn(List.of());

        assertThat(service.verifyCode(userId, "000000")).isFalse();
    }

    @Test
    void isEnrolled_enabledRow_returnsTrue() {
        UUID userId = UUID.randomUUID();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.of(
                AuthUserMfaEntity.builder().userId(userId).enabled(true).build()));

        assertThat(service.isEnrolled(userId)).isTrue();
    }

    @Test
    void isEnrolled_noRow_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(authUserMfaRepo.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.isEnrolled(userId)).isFalse();
    }
}
