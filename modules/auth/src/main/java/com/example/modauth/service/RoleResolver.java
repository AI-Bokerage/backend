package com.example.modauth.service;

import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.domain.Role;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * The caller's platform {@link Role} isn't a JWT claim (gen-auth-starter's
 * token only carries {@code user_type}) — it's a lookup against
 * {@code modauth_user_roles}. Shared here since Invitations, Teams and
 * People all need it.
 */
@Component
@RequiredArgsConstructor
public class RoleResolver {

    private final ModAuthUserRoleJpaRepository roleRepo;

    public Role resolve(AuthenticatedUser user) {
        Optional<ModAuthUserRoleEntity> row = roleRepo.findById(user.getUserId());
        if (row.isPresent()) {
            return row.get().getRole();
        }
        if (user.getUserType() == UserType.SUPER_ADMIN) {
            return Role.SUPER_ADMIN;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No role assigned");
    }

    public void requireTenantAdmin(AuthenticatedUser user) {
        if (resolve(user) != Role.TENANT_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant Admin only");
        }
    }
}
