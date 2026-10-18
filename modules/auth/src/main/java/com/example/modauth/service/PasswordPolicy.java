package com.example.modauth.service;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.modauth.entity.PasswordHistoryEntity;
import com.example.modauth.repository.PasswordHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * SOW 1.4 — 12-character minimum and no reuse of the current or last 5
 * passwords. gen-auth-starter only enforces 8 characters and has no hook, so
 * this is applied by modauth's own wrappers around the starter's
 * change-password and reset flows (PolicyChangePasswordService,
 * PolicyMagicLinkService) and on AcceptInvitationRequest.
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final String TOO_SHORT_MESSAGE = "Password must be at least " + MIN_LENGTH + " characters";

    private final AuthUserJpaRepository userRepo;
    private final PasswordHistoryJpaRepository historyRepo;
    private final PasswordHasher passwordHasher;

    public static void requireMinLength(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TOO_SHORT_MESSAGE);
        }
    }

    /**
     * 400 if too short or a recent password. Returns the user's current hash,
     * which the caller passes to {@link #remember} once the change succeeds.
     */
    public String requireAcceptable(UUID userId, String newPassword) {
        requireMinLength(newPassword);
        String currentHash = userRepo.findById(userId).map(AuthUserEntity::getPasswordHash).orElse(null);
        boolean reused = Stream.concat(
                        Stream.ofNullable(currentHash),
                        historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream()
                                .map(PasswordHistoryEntity::getPasswordHash))
                .anyMatch(hash -> passwordHasher.verify(newPassword, hash));
        if (reused) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You can't reuse your current password or any of your last 5");
        }
        return currentHash;
    }

    public void remember(UUID userId, String previousHash) {
        if (previousHash == null) return;
        historyRepo.save(PasswordHistoryEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .passwordHash(previousHash)
                .build());
    }
}
