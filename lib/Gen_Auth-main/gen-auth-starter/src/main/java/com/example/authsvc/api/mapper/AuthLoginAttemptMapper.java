package com.example.authsvc.api.mapper;

import com.example.authsvc.api.dto.request.LoginAttemptRequest;
import com.example.authsvc.infrastructure.persistence.entity.AuthLoginAttemptEntity;

import java.util.UUID;

public final class AuthLoginAttemptMapper {

    private AuthLoginAttemptMapper() {}

    public static AuthLoginAttemptEntity toEntity(LoginAttemptRequest request) {
        return AuthLoginAttemptEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(request.tenantId())
                .userId(request.userId())
                .email(request.email())
                .ipAddress(request.ip())
                .userAgent(request.userAgent())
                .success(request.success())
                .failureReason(request.failureReason())
                .build();
    }
}
