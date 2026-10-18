package com.example.modauth.controller;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.modauth.dto.InternalUserRoleResponse;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.service.TenantAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Service-to-service role lookup — the piece any Broker/Team-Lead/Tenant-Admin
 * facing feature outside modules/auth needs, since the JWT only carries
 * {@code user_type} and this module's own {@link com.example.modauth.domain.Role}
 * lives solely in {@code modauth_user_roles}. Gated by the shared-secret
 * header like every other {@code /internal/**} endpoint — see
 * InternalInvitationController.
 */
@Tag(name = "Internal — Users", description = "Service-to-service role/team lookup")
@RestController
@RequestMapping("/internal/modauth/users")
@RequiredArgsConstructor
public class InternalUserRoleController {

    private final ModAuthUserRoleJpaRepository roleRepo;
    private final ModAuthUserLookupRepository userLookupRepo;
    private final TenantAccessService tenantAccessService;

    @Operation(summary = "Look up a person's platform role/team by user id")
    @ApiResponse(responseCode = "200", description = "Role found")
    @ApiResponse(responseCode = "404", description = "No role assigned to this user id")
    @GetMapping("/{userId}")
    public InternalUserRoleResponse get(@PathVariable UUID userId) {
        ModAuthUserRoleEntity role = roleRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No role assigned to this user id"));
        AuthUserEntity user = userLookupRepo.findById(userId).orElse(null);
        return new InternalUserRoleResponse(
                role.getUserId(), role.getTenantId(), role.getRole(), role.getTeamId(), role.getName(),
                user != null && user.isActive(),
                tenantAccessService.isBlocked(role.getTenantId()));
    }
}
