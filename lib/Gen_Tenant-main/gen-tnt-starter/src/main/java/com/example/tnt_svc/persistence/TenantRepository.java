package com.example.tnt_svc.persistence;

import com.example.tnt_svc.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByIdempotencyKey(String idempotencyKey);
}
