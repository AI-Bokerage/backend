package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserMfaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthUserMfaJpaRepository extends JpaRepository<AuthUserMfaEntity, UUID> {

    Optional<AuthUserMfaEntity> findByUserId(UUID userId);
}
