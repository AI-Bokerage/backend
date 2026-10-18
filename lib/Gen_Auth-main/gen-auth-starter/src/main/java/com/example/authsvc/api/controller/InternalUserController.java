package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.InternalCreateUserRequest;
import com.example.authsvc.api.dto.response.RegisterResponse;
import com.example.authsvc.application.impl.InternalSessionRevocationService;
import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal service-to-service endpoints for user lifecycle management.
 *
 * <p>Protected by {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter} —
 * callers must include {@code X-Internal-Secret: <INTERNAL_SERVICE_SECRET>} header.
 * These endpoints are not intended for external clients or self-service users.
 */
@Slf4j
@RestController
@RequestMapping("/internal/auth/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalSessionRevocationService revocationService;
    private final RegisterService                  registerService;
    private final AuthUserJpaRepository             authUserJpaRepository;

    /**
     * Creates a user on behalf of a trusted internal caller. The only path that
     * can set {@code roleId} at creation time — public self-registration never can.
     */
    @PostMapping
    public ResponseEntity<RegisterResponse> createUser(@Valid @RequestBody InternalCreateUserRequest request) {
        UUID userId = registerService.register(
                request.getEmail(), request.getPassword(), request.getTenantId(), request.getRoleId(),
                request.getId(), request.getUserType());
        log.info("internal.user_created userId={} tenantId={}", userId, request.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(userId));
    }

    /**
     * Revokes all active sessions and refresh tokens for the given user.
     *
     * <p>Returns {@code 204 No Content} regardless of whether the user existed
     * or had any active sessions (idempotent).
     *
     * @param userId   the user whose sessions to revoke
     * @param tenantId tenant context (optional, used for audit logging)
     */
    @PostMapping("/{userId}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID tenantId) {
        log.info("internal.revoke_sessions userId={} tenantId={}", userId, tenantId);
        revocationService.revokeAllForUser(userId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Checks whether any account (active or inactive) exists for the given
     * email — used by internal invite flows to reject duplicate invites
     * before sending them.
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> exists(@RequestParam String email) {
        return ResponseEntity.ok(authUserJpaRepository.existsByEmail(email));
    }
}
