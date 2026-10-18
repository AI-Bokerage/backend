package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;

import java.util.UUID;

public record PermissionResponse(UUID id, String code, String description) {

    public static PermissionResponse from(PermissionEntity permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getDescription());
    }
}
