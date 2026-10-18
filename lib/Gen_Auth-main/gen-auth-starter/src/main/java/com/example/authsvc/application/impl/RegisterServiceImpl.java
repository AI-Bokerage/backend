package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.common.exception.EmailAlreadyExistsException;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserRoleEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserRoleId;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserRoleJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {

    private final AuthUserJpaRepository userRepo;
    private final AuthUserRoleJpaRepository userRoleRepo;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public UUID register(String email, String password, UUID tenantId, UUID roleId, UUID id, UserType userType) {
        if (id != null && userRepo.existsById(id)) {
            return id;
        }

        if (userRepo.findByEmailAndActiveTrue(email).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        UUID resolvedTenantId = tenantId != null ? tenantId : TenantConstants.PLATFORM_TENANT_ID;
        UUID resolvedId = id != null ? id : UUID.randomUUID();
        UserType resolvedUserType = userType != null ? userType : UserType.TENANT_USER;

        AuthUserEntity user = AuthUserEntity.builder()
                .id(resolvedId)
                .tenantId(resolvedTenantId)
                .email(email)
                .passwordHash(passwordHasher.hash(password))
                .userType(resolvedUserType)
                .roleId(roleId)
                .active(true)
                .build();

        userRepo.save(user);

        if (roleId != null) {
            userRoleRepo.save(new AuthUserRoleEntity(
                    new AuthUserRoleId(user.getId(), roleId), resolvedTenantId, null));
        }

        return user.getId();
    }
}
