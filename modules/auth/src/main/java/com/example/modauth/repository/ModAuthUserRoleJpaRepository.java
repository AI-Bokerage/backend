package com.example.modauth.repository;

import com.example.modauth.entity.ModAuthUserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModAuthUserRoleJpaRepository extends JpaRepository<ModAuthUserRoleEntity, UUID> {

    List<ModAuthUserRoleEntity> findByTenantId(UUID tenantId);

    List<ModAuthUserRoleEntity> findByTeamId(UUID teamId);
}
