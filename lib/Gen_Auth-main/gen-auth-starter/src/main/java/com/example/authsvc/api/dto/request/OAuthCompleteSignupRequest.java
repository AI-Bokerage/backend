package com.example.authsvc.api.dto.request;

import com.example.authsvc.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record OAuthCompleteSignupRequest(
        @NotBlank(message = "Setup token is required") String setupToken,
        @ValidPassword String password
) {}
