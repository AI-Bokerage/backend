package com.example.modauth.repository;

import com.example.modauth.entity.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamJpaRepository extends JpaRepository<TeamEntity, UUID> {

    List<TeamEntity> findByTenantId(UUID tenantId);

    boolean existsByTeamLeadUserId(UUID teamLeadUserId);
}
