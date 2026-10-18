package com.example.authsvc.application.impl;

import com.example.authsvc.common.exception.EmailAlreadyExistsException;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserRoleEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserRoleJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterServiceImplTest {

    @Mock private AuthUserJpaRepository userRepo;
    @Mock private AuthUserRoleJpaRepository authUserRoleRepo;
    @Mock private PasswordHasher passwordHasher;

    private RegisterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegisterServiceImpl(userRepo, authUserRoleRepo, passwordHasher);
    }

    @Test
    void registersWithPlatformSentinelWhenTenantIdOmitted() {
        when(userRepo.findByEmailAndActiveTrue("user@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("password123")).thenReturn("hashed");

        UUID userId = service.register("user@example.com", "password123", null, null, null, null);

        assertNotNull(userId);
        ArgumentCaptor<AuthUserEntity> captor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(captor.capture());
        AuthUserEntity saved = captor.getValue();
        assertEquals("user@example.com", saved.getEmail());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals(TenantConstants.PLATFORM_TENANT_ID, saved.getTenantId());
        assertEquals(UserType.TENANT_USER, saved.getUserType());
        assertNull(saved.getRoleId());
        assertEquals(userId, saved.getId());
        verify(authUserRoleRepo, never()).save(any());
    }

    @Test
    void registersWithSuppliedTenantIdAndRoleId() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(userRepo.findByEmailAndActiveTrue("admin@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("password123")).thenReturn("hashed");

        UUID userId = service.register("admin@example.com", "password123", tenantId, roleId, null, null);

        ArgumentCaptor<AuthUserEntity> captor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(captor.capture());
        assertEquals(tenantId, captor.getValue().getTenantId());
        assertEquals(roleId, captor.getValue().getRoleId());

        verify(authUserRoleRepo).save(argThat((AuthUserRoleEntity role) ->
                role.getId().getUserId().equals(userId)
                        && role.getId().getRoleId().equals(roleId)
                        && role.getTenantId().equals(tenantId)));
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepo.findByEmailAndActiveTrue("taken@example.com"))
                .thenReturn(Optional.of(AuthUserEntity.builder().id(UUID.randomUUID()).build()));

        assertThrows(EmailAlreadyExistsException.class,
                () -> service.register("taken@example.com", "password123", null, null, null, null));

        verify(userRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registersWithSuppliedIdAndUserType() {
        UUID id = UUID.randomUUID();
        when(userRepo.existsById(id)).thenReturn(false);
        when(userRepo.findByEmailAndActiveTrue("client@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("password123")).thenReturn("hashed");

        UUID userId = service.register("client@example.com", "password123", null, null, id, UserType.CLIENT);

        assertEquals(id, userId);
        ArgumentCaptor<AuthUserEntity> captor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(captor.capture());
        assertEquals(id, captor.getValue().getId());
        assertEquals(UserType.CLIENT, captor.getValue().getUserType());
    }

    @Test
    void suppliedIdAlreadyExists_isIdempotentAndSkipsCreate() {
        UUID id = UUID.randomUUID();
        when(userRepo.existsById(id)).thenReturn(true);

        UUID userId = service.register("client@example.com", "password123", null, null, id, UserType.CLIENT);

        assertEquals(id, userId);
        verify(userRepo, never()).save(any());
        verify(userRepo, never()).findByEmailAndActiveTrue(any());
    }
}
