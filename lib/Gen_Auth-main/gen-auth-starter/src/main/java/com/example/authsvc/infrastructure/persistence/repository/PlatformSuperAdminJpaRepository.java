package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for the {@code platform_super_admin} table.
 * Only one active row is expected at any time.
 */
@Repository
public interface PlatformSuperAdminJpaRepository extends JpaRepository<PlatformSuperAdminEntity, UUID> {

    Optional<PlatformSuperAdminEntity> findByEmailAndActiveTrue(String email);
}
