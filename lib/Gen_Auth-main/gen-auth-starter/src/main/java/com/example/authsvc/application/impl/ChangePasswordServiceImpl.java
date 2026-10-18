package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ChangePasswordRequest;
import com.example.authsvc.application.service.ChangePasswordService;
import com.example.authsvc.common.exception.BadRequestException;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ChangePasswordServiceImpl implements ChangePasswordService {

    private final AuthUserJpaRepository userRepo;
    private final AuthSessionJpaRepository sessionRepo;
    private final AuthRefreshTokenJpaRepository refreshTokenRepo;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordHasher passwordHasher;

    private final AuthEventPublisher authEventPublisher;

    public ChangePasswordServiceImpl(
            AuthUserJpaRepository userRepo,
            AuthSessionJpaRepository sessionRepo,
            AuthRefreshTokenJpaRepository refreshTokenRepo,
            RefreshTokenStore refreshTokenStore,
            PasswordHasher passwordHasher,
            @Autowired(required = false) AuthEventPublisher authEventPublisher) {
        this.userRepo = userRepo;
        this.sessionRepo = sessionRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordHasher = passwordHasher;
        this.authEventPublisher = authEventPublisher;
    }

    @Override
    @Transactional
    public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request) {
        UUID userId = principal != null ? principal.getUserId() : null;
        String sessionId = principal != null ? principal.getSessionId() : null;

        log.info("password.change.started userId={} sessionId={}", userId, sessionId);

        try {
            if (principal == null) {
                throw new UnauthorizedException();
            }

            AuthUserEntity user = userRepo.findById(principal.getUserId())
                    .filter(AuthUserEntity::isActive)
                    .orElseThrow(UnauthorizedException::new);

            if (!passwordHasher.verify(request.currentPassword(), user.getPasswordHash())) {
                throw new UnauthorizedException("Current password is incorrect");
            }

            if (request.currentPassword().equals(request.newPassword())) {
                throw new BadRequestException("New password must be different from current password");
            }

            Instant now = Instant.now();
            UUID currentSessionId = UUID.fromString(principal.getSessionId());
            List<UUID> otherFamilyIds = refreshTokenRepo.findActiveFamilyIdsByUserIdExcludingSession(
                    user.getId(), currentSessionId);

            user.setPasswordHash(passwordHasher.hash(request.newPassword()));
            userRepo.save(user);

            sessionRepo.deactivateAllByUserIdExceptSession(user.getId(), currentSessionId, now);
            refreshTokenRepo.revokeAllByUserIdExceptSession(user.getId(), currentSessionId, now);
            otherFamilyIds.forEach(refreshTokenStore::revokeByFamilyId);

            if (authEventPublisher != null) {
                authEventPublisher.publishPasswordChanged(user.getId(), currentSessionId, now);
                authEventPublisher.publishAuditPasswordChanged(user.getId(), user.getTenantId(), now);
            }
            log.info("password.change.success userId={} sessionId={}", user.getId(), currentSessionId);
        } catch (RuntimeException ex) {
            log.warn("password.change.failed userId={} sessionId={} reason={}",
                    userId, sessionId, ex.getMessage());
            throw ex;
        }
    }
}
