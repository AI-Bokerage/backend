package com.example.authsvc.api.dto.request;

import com.example.authsvc.common.validation.PasswordConfirmationRequest;
import com.example.authsvc.common.validation.PasswordsMatch;
import com.example.authsvc.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

@PasswordsMatch
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @ValidPassword String newPassword,
        @NotBlank String confirmPassword
) implements PasswordConfirmationRequest {}
