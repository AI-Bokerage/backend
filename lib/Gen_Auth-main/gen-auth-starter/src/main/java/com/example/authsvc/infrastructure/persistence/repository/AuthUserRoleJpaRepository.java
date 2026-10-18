package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserRoleEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuthUserRoleJpaRepository extends JpaRepository<AuthUserRoleEntity, AuthUserRoleId> {

    /**
     * Returns all role UUIDs assigned to the given user.
     * Used by Phase 4 JWT generation to build the {@code role_ids[]} claim.
     */
    @Query("SELECT e.id.roleId FROM AuthUserRoleEntity e WHERE e.id.userId = :userId")
    List<UUID> findRoleIdsByUserId(@Param("userId") UUID userId);

    /**
     * Returns all role assignment rows for a user.
     * Used by Phase 5 consumers that inspect the full assignment list.
     */
    List<AuthUserRoleEntity> findByIdUserId(UUID userId);

    /**
     * Deletes all role assignments for a user.
     * Called by {@code UserDeactivatedConsumer} to clear the projection when
     * a user is deactivated.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AuthUserRoleEntity e WHERE e.id.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    /**
     * Returns {@code true} if the user has at least one role in the projection.
     */
    boolean existsByIdUserId(UUID userId);
}
