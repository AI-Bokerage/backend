package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.InternalCreateUserRequest;
import com.example.authsvc.application.impl.InternalSessionRevocationService;
import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

    @Mock private InternalSessionRevocationService revocationService;
    @Mock private RegisterService registerService;
    @Mock private AuthUserJpaRepository authUserJpaRepository;

    private InternalUserController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalUserController(revocationService, registerService, authUserJpaRepository);
    }

    @Test
    void createUserForwardsRoleIdToRegisterService() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        InternalCreateUserRequest request = new InternalCreateUserRequest();
        request.setEmail("admin@example.com");
        request.setPassword("password123");
        request.setTenantId(tenantId);
        request.setRoleId(roleId);
        when(registerService.register("admin@example.com", "password123", tenantId, roleId, null, null))
                .thenReturn(newUserId);

        ResponseEntity<?> response = controller.createUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(registerService).register("admin@example.com", "password123", tenantId, roleId, null, null);
    }

    @Test
    void createUserForwardsIdAndUserTypeToRegisterService() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        InternalCreateUserRequest request = new InternalCreateUserRequest();
        request.setEmail("client@example.com");
        request.setPassword("password123");
        request.setTenantId(tenantId);
        request.setId(id);
        request.setUserType(UserType.CLIENT);
        when(registerService.register("client@example.com", "password123", tenantId, null, id, UserType.CLIENT))
                .thenReturn(id);

        ResponseEntity<?> response = controller.createUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(registerService).register("client@example.com", "password123", tenantId, null, id, UserType.CLIENT);
    }

    @Test
    void exists_emailHasAccount_returnsTrue() {
        when(authUserJpaRepository.existsByEmail("taken@example.com")).thenReturn(true);

        ResponseEntity<Boolean> response = controller.exists("taken@example.com");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void exists_emailUnused_returnsFalse() {
        when(authUserJpaRepository.existsByEmail("free@example.com")).thenReturn(false);

        ResponseEntity<Boolean> response = controller.exists("free@example.com");

        assertThat(response.getBody()).isFalse();
    }
}
