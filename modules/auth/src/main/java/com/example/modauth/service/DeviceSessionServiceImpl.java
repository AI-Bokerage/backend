package com.example.modauth.service;

import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.SessionSummaryResponse;
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
public class DeviceSessionServiceImpl implements DeviceSessionService {

    private final AuthSessionJpaRepository sessionRepo;

    @Override
    public List<SessionSummaryResponse> listMine(AuthenticatedUser caller) {
        return sessionRepo.findAllByUserIdAndActiveTrue(caller.getUserId()).stream()
                .map(s -> toResponse(s, caller))
                .toList();
    }

    @Override
    @Transactional
    public void revoke(AuthenticatedUser caller, UUID sessionId) {
        AuthSessionEntity session = sessionRepo.findByIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getUserId().equals(caller.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }
        session.setActive(false);
        session.setRevokedAt(Instant.now());
        sessionRepo.save(session);
        log.info("session.revoked id={} userId={}", sessionId, caller.getUserId());
    }

    private static SessionSummaryResponse toResponse(AuthSessionEntity session, AuthenticatedUser caller) {
        boolean isCurrent = caller.getSessionId() != null && caller.getSessionId().equals(session.getId().toString());
        return new SessionSummaryResponse(
                session.getId(), session.getIpAddress(), session.getUserAgent(),
                session.getCreatedAt(), session.getLastActivityAt(), session.getExpiresAt(), isCurrent);
    }
}
