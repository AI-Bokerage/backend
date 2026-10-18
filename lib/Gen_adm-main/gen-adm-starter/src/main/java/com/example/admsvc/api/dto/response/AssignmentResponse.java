package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;

import java.util.UUID;

public record AssignmentResponse(UUID userId, UUID roleId) {

    public static AssignmentResponse from(UserRoleAssignmentEntity assignment) {
        return new AssignmentResponse(assignment.getUserId(), assignment.getRoleId());
    }
}
