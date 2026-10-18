package com.example.authsvc.api.mapper;

import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;

import java.time.Duration;
import java.time.Instant;

public final class LoginResponseMapper {

    private LoginResponseMapper() {}

    public static LoginResponse toResponse(AuthUserEntity user, Instant accessTokenExpiry,
                                            String accessToken) {
        return new LoginResponse(user.getId(), user.getEmail(), accessTokenExpiry, accessToken);
    }

    public static LoginResult toResult(LoginResponse response, String accessToken,
                                       String refreshToken, Duration accessTokenTtl,
                                       Duration refreshTokenTtl) {
        return new LoginResult(response, accessToken, refreshToken, accessTokenTtl, refreshTokenTtl);
    }
}
