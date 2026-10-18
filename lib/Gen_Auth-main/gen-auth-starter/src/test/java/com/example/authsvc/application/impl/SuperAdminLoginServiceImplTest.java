package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminLoginServiceImplTest {

    @Mock private PlatformSuperAdminJpaRepository superAdminRepo;
    @Mock private LockoutService                  lockoutService;
    @Mock private LoginExecutionService            loginExecutor;

    @InjectMocks
    private SuperAdminLoginServiceImpl service;

    @Test
    void login_unknownEmail_throwsAndRecordsFailure() {
        when(superAdminRepo.findByEmailAndActiveTrue("admin@example.com")).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("whatever");

        assertThatThrownBy(() -> service.login(request, "127.0.0.1", "curl/8.0"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginExecutor).handleFailure(null, null, "admin@example.com", "127.0.0.1", "curl/8.0", "INVALID_CREDENTIALS");
    }

    @Test
    void login_knownEmail_delegatesToLoginExecutionService() {
        UUID superAdminId = UUID.randomUUID();
        PlatformSuperAdminEntity superAdmin = PlatformSuperAdminEntity.builder()
                .id(superAdminId).email("admin@example.com").passwordHash("hashed").active(true).build();
        when(superAdminRepo.findByEmailAndActiveTrue("admin@example.com")).thenReturn(Optional.of(superAdmin));

        LoginResult expected = org.mockito.Mockito.mock(LoginResult.class);
        when(loginExecutor.executeLogin(any(), any(), any(), any(), anyLong())).thenReturn(expected);

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("correct horse battery staple");
        LoginResult result = service.login(request, "127.0.0.1", "curl/8.0");

        org.assertj.core.api.Assertions.assertThat(result).isSameAs(expected);
        verify(loginExecutor).executeLogin(any(), org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"), org.mockito.ArgumentMatchers.eq("curl/8.0"), anyLong());
    }
}
