package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record RoleResponse(UUID id, String name, String description, Set<String> permissionCodes) {

    public static RoleResponse from(RoleEntity role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions().stream().map(PermissionEntity::getCode).collect(Collectors.toSet()));
    }
}
