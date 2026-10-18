package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.domain.port.OtpStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock private OtpStore otpStore;
    @Mock private EmailService emailService;

    @Test
    void requestOtp_storesHashNotPlaintext_emailsPlaintextCode() {
        OtpServiceImpl service = new OtpServiceImpl(otpStore, emailService);
        setTtlMinutes(service, 5);
        UUID expectedId = UUID.randomUUID();
        when(otpStore.issue(anyString(), eq(Duration.ofMinutes(5)))).thenReturn(expectedId);

        UUID otpId = service.requestOtp("user@example.com", "login");

        assertThat(otpId).isEqualTo(expectedId);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpStore).issue(hashCaptor.capture(), eq(Duration.ofMinutes(5)));
        String storedHash = hashCaptor.getValue();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpCode(eq("user@example.com"), codeCaptor.capture(), eq("login"));
        String plaintextCode = codeCaptor.getValue();

        assertThat(plaintextCode).hasSize(6).containsOnlyDigits();
        assertThat(storedHash).isNotEqualTo(plaintextCode);
        assertThat(storedHash).isEqualTo(sha256Hex(plaintextCode));
    }

    @Test
    void verifyOtp_delegatesHashedCodeToStore_singleCall() {
        OtpServiceImpl service = new OtpServiceImpl(otpStore, emailService);
        UUID otpId = UUID.randomUUID();
        when(otpStore.verifyAndConsume(eq(otpId), anyString())).thenReturn(true);

        boolean result = service.verifyOtp(otpId, "123456");

        assertThat(result).isTrue();
        verify(otpStore, times(1)).verifyAndConsume(eq(otpId), eq(sha256Hex("123456")));
    }

    @Test
    void verifyOtp_wrongCode_returnsFalse() {
        OtpServiceImpl service = new OtpServiceImpl(otpStore, emailService);
        UUID otpId = UUID.randomUUID();
        when(otpStore.verifyAndConsume(eq(otpId), anyString())).thenReturn(false);

        boolean result = service.verifyOtp(otpId, "000000");

        assertThat(result).isFalse();
        verify(emailService, never()).sendOtpCode(any(), any(), any());
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setTtlMinutes(OtpServiceImpl service, int value) {
        try {
            var field = OtpServiceImpl.class.getDeclaredField("ttlMinutes");
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
