package com.example.authsvc.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpRequestDto(
        @Email @NotBlank String toEmail,
        @NotBlank String purpose
) {}
