package com.example.authsvc.domain.port;

import com.example.authsvc.domain.model.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStore {

    void save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeByTokenHash(String tokenHash);

    void revokeAllBySessionId(UUID sessionId);

    void revokeAllByUserId(UUID userId);

    void revokeByFamilyId(UUID familyId);
}
