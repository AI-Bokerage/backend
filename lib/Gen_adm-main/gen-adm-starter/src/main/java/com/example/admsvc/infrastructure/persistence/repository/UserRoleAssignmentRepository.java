package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, UUID> {

    List<UserRoleAssignmentEntity> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<UserRoleAssignmentEntity> findAllByTenantId(UUID tenantId);

    Optional<UserRoleAssignmentEntity> findByTenantIdAndUserIdAndRoleId(UUID tenantId, UUID userId, UUID roleId);

    void deleteByTenantIdAndUserIdAndRoleId(UUID tenantId, UUID userId, UUID roleId);
}
