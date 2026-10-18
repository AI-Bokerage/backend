package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ClientTokenRequest;
import com.example.authsvc.api.dto.response.ClientTokenResponse;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientTokenServiceImplTest {

    @Mock private JwtUtils                 jwtUtils;
    @Mock private AuthSessionJpaRepository authSessionRepository;
    @Mock private TenantSlugResolver       tenantSlugResolver;

    @Test
    void issue_noSlugProvided_resolvesViaFallbackAndPersistsSession() {
        ClientTokenServiceImpl service = new ClientTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver);
        setExpirationMinutes(service, 15);

        UUID clientUserId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ClientTokenRequest request = new ClientTokenRequest(clientUserId, tenantId, null, roleId, "session-abc");

        ClientTokenResponse response = service.issue(request);

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.expiresIn()).isEqualTo(15 * 60);
        assertThat(response.sessionId()).isEqualTo("session-abc");

        ArgumentCaptor<AuthSessionEntity> captor = ArgumentCaptor.forClass(AuthSessionEntity.class);
        verify(authSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserType()).isEqualTo(UserType.CLIENT);
        assertThat(captor.getValue().isImpersonation()).isFalse();
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void issue_slugProvided_skipsResolverFallback() {
        ClientTokenServiceImpl service = new ClientTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver);
        setExpirationMinutes(service, 15);

        UUID clientUserId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ClientTokenRequest request = new ClientTokenRequest(clientUserId, tenantId, "acme", roleId, "session-abc");

        service.issue(request);

        verify(tenantSlugResolver, never()).resolve(any());
    }

    private static void setExpirationMinutes(ClientTokenServiceImpl service, int value) {
        try {
            var field = ClientTokenServiceImpl.class.getDeclaredField("expirationMinutes");
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
