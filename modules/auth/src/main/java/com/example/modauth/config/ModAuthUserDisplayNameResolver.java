package com.example.modauth.config;

import com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Same situation as {@link ModAuthTenantSlugResolver} — the starter's
 * conditional-default {@code NoOpUserDisplayNameResolver} didn't register,
 * so this supplies the same no-op behavior directly.
 *
 * <p>ponytail: no display-name source in this module yet — upgrade to read
 * from modauth_user_roles / a real profile table if the JWT ever needs it.
 */
@Component
public class ModAuthUserDisplayNameResolver implements UserDisplayNameResolver {

    @Override
    public String resolve(UUID userId) {
        return "";
    }
}
