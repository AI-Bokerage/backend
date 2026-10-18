package com.example.modauth.service;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.domain.Role;
import com.example.modauth.dto.PersonResponse;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeopleServiceImpl implements PeopleService {

    private final ModAuthUserRoleJpaRepository roleRepo;
    private final ModAuthUserLookupRepository userLookupRepo;
    private final AuthRefreshTokenJpaRepository refreshTokenRepo;
    private final RoleResolver roleResolver;
    private final PersonMapper personMapper;
    private final CrmClient crmClient;

    @Override
    public List<PersonResponse> list(AuthenticatedUser admin) {
        roleResolver.requireTenantAdmin(admin);
        return roleRepo.findByTenantId(admin.getTenantId()).stream()
                .filter(r -> r.getRole() != Role.TENANT_ADMIN)
                .map(personMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deactivate(AuthenticatedUser admin, UUID userId) {
        roleResolver.requireTenantAdmin(admin);

        ModAuthUserRoleEntity target = roleRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));
        if (!target.getTenantId().equals(admin.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your brokerage's person");
        }
        if (target.getRole() == Role.TENANT_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot deactivate a Tenant Admin here");
        }

        // "Do they still have clients assigned?" (Flow 2 / SOW 2.11) — a
        // Broker, Assistant or Team Lead can all hold leads directly (Flow
        // 3's "own book"), so the same check applies to any of them.
        int assignedCount = crmClient.activeAssignedLeadCount(admin.getTenantId(), userId);
        if (assignedCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reassign their " + assignedCount + " clients first");
        }

        AuthUserEntity user = userLookupRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));
        user.setActive(false);
        userLookupRepo.save(user);
        refreshTokenRepo.revokeAllByUserId(userId, Instant.now());

        log.info("people.deactivated userId={} tenantId={}", userId, admin.getTenantId());
    }
}
