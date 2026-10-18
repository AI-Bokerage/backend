package com.example.authsvc.infrastructure.security.jwt;

import java.util.UUID;

/**
 * Resolves a human-readable display name for the {@code username} claim at
 * login/rotation/impersonation-token time.
 *
 * <p>A standalone auth service has no user-profile directory of its own, so the
 * default implementation ({@link NoOpUserDisplayNameResolver}) always returns an
 * empty string. A host application that owns real user-profile data supplies its
 * own {@code UserDisplayNameResolver} bean — it wins over the default automatically
 * ({@code @ConditionalOnMissingBean}) — rather than this library reaching into
 * another service's database.
 */
public interface UserDisplayNameResolver {

    String resolve(UUID userId);
}
