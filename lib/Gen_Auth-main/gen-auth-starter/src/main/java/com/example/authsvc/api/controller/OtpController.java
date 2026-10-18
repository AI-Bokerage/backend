package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.OtpRequestDto;
import com.example.authsvc.api.dto.request.OtpVerifyDto;
import com.example.authsvc.api.dto.response.OtpIssuedResponse;
import com.example.authsvc.api.dto.response.OtpVerifiedResponse;
import com.example.authsvc.application.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal caller-supplied-email OTP issue/verify endpoints. Only registered
 * when {@code app.otp.enabled=true}.
 *
 * <p>Protected by {@code X-Internal-Secret} via
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * through the {@code /internal/**} path prefix.
 */
@Slf4j
@RestController
@RequestMapping("/internal/otp")
@ConditionalOnProperty(prefix = "app.otp", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/request")
    public ResponseEntity<OtpIssuedResponse> request(@Valid @RequestBody OtpRequestDto request) {
        return ResponseEntity.ok(new OtpIssuedResponse(otpService.requestOtp(request.toEmail(), request.purpose())));
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifiedResponse> verify(@Valid @RequestBody OtpVerifyDto request) {
        return ResponseEntity.ok(new OtpVerifiedResponse(otpService.verifyOtp(request.otpId(), request.code())));
    }
}
