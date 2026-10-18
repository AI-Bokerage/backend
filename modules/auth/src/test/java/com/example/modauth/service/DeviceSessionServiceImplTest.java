package com.example.modauth.service;

import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.SessionSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Devices I'm signed in on" — list + revoke, scoped to the caller's own sessions. */
@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceImplTest {

    @Mock private AuthSessionJpaRepository sessionRepo;

    private DeviceSessionServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private AuthenticatedUser caller;

    @BeforeEach
    void setUp() {
        service = new DeviceSessionServiceImpl(sessionRepo);
        caller = new AuthenticatedUser(userId, UUID.randomUUID(), "acme", List.of(), UserType.TENANT_USER,
                "current-session-id", null, "jti");
    }

    private AuthSessionEntity session(UUID id, UUID owner) {
        return AuthSessionEntity.builder().id(id).userId(owner).tenantId(UUID.randomUUID())
                .userType(UserType.TENANT_USER).active(true).createdAt(Instant.now()).build();
    }

    @Test
    void listMineMarksTheCallersOwnSessionAsCurrent() {
        UUID currentId = UUID.fromString("00000000-0000-0000-0000-00000000cafe");
        AuthenticatedUser callerWithMatchingSession = new AuthenticatedUser(userId, UUID.randomUUID(), "acme",
                List.of(), UserType.TENANT_USER, currentId.toString(), null, "jti");
        AuthSessionEntity current = session(currentId, userId);
        AuthSessionEntity other = session(UUID.randomUUID(), userId);
        when(sessionRepo.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(current, other));

        List<SessionSummaryResponse> result = service.listMine(callerWithMatchingSession);

        assertThat(result).hasSize(2);
        assertThat(result.stream().filter(SessionSummaryResponse::isCurrent)).hasSize(1);
        assertThat(result.stream().filter(r -> r.id().equals(currentId)).findFirst().get().isCurrent()).isTrue();
    }

    @Test
    void revokeDeactivatesTheCallersOwnSession() {
        UUID sessionId = UUID.randomUUID();
        AuthSessionEntity mine = session(sessionId, userId);
        when(sessionRepo.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(mine));

        service.revoke(caller, sessionId);

        assertThat(mine.isActive()).isFalse();
        assertThat(mine.getRevokedAt()).isNotNull();
        verify(sessionRepo).save(mine);
    }

    @Test
    void cannotRevokeSomeoneElsesSession() {
        UUID sessionId = UUID.randomUUID();
        AuthSessionEntity someoneElses = session(sessionId, UUID.randomUUID());
        when(sessionRepo.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.revoke(caller, sessionId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not your session");

        verify(sessionRepo, never()).save(someoneElses);
    }

    @Test
    void revokingAnAlreadyInactiveSessionIs404() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepo.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(caller, sessionId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Session not found");
    }
}
