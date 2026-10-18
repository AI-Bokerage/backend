package com.example.authsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OtpVerifyDto(
        @NotNull UUID otpId,
        @NotBlank String code
) {}
