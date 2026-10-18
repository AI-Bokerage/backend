package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.response.ServiceTokenResponse;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenServiceImplTest {

    @Mock private JwtUtils jwtUtils;

    @Test
    void issue_returnsFiveMinuteSuperAdminToken() {
        ServiceTokenServiceImpl service = new ServiceTokenServiceImpl(jwtUtils);
        setExpirationMinutes(service, 5);
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ServiceTokenResponse response = service.issue("sup-svc");

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.expiresIn()).isEqualTo(5 * 60);

        ArgumentCaptor<JwtClaims> captor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateAccessToken(captor.capture());
        JwtClaims claims = captor.getValue();
        assertThat(claims.userId()).isEqualTo(TenantConstants.SERVICE_ACCOUNT_ID);
        assertThat(claims.tenantId()).isEqualTo(TenantConstants.PLATFORM_TENANT_ID);
        assertThat(claims.tenantSlug()).isEqualTo("platform");
        assertThat(claims.roleIds()).isEmpty();
        assertThat(claims.userType()).isEqualTo(UserType.SUPER_ADMIN);
        assertThat(claims.sessionId()).isEqualTo("svc:sup-svc");
    }

    private static void setExpirationMinutes(ServiceTokenServiceImpl service, int value) {
        try {
            var field = ServiceTokenServiceImpl.class.getDeclaredField("expirationMinutes");
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
