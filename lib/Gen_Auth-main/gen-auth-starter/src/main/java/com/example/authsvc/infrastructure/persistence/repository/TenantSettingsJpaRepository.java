package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.TenantSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TenantSettingsJpaRepository extends JpaRepository<TenantSettingsEntity, UUID> {
}
