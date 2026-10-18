package com.example.modauth.service;

import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.entity.TenantAccessEntity;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.repository.TenantAccessJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Flow 1 "Suspend → everyone in that brokerage is blocked at login;
 * Reactivate → access restored" (cancellation blocks too). The platform
 * pushes each status change here; login, token refresh and the CRM's role
 * lookup all consult {@link #isBlocked}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAccessService {

    private final TenantAccessJpaRepository accessRepo;
    private final ModAuthUserRoleJpaRepository roleRepo;
    private final AuthRefreshTokenJpaRepository refreshTokenRepo;
    private final AuthSessionJpaRepository sessionRepo;

    public boolean isBlocked(UUID tenantId) {
        return tenantId != null && accessRepo.findById(tenantId).map(TenantAccessEntity::isBlocked).orElse(false);
    }

    @Transactional
    public void setAccess(UUID tenantId, boolean blocked, String status) {
        accessRepo.save(TenantAccessEntity.builder().tenantId(tenantId).blocked(blocked).status(status).build());
        if (blocked) {
            // Signed-in people are signed out too, not just future logins.
            Instant now = Instant.now();
            for (ModAuthUserRoleEntity person : roleRepo.findByTenantId(tenantId)) {
                refreshTokenRepo.revokeAllByUserId(person.getUserId(), now);
                sessionRepo.deactivateAllByUserId(person.getUserId(), now);
            }
        }
        log.info("tenant.access_changed tenantId={} blocked={} status={}", tenantId, blocked, status);
    }
}
