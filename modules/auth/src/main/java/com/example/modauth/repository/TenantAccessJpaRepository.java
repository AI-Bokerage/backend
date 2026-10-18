package com.example.modauth.repository;

import com.example.modauth.entity.TenantAccessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TenantAccessJpaRepository extends JpaRepository<TenantAccessEntity, UUID> {
}
