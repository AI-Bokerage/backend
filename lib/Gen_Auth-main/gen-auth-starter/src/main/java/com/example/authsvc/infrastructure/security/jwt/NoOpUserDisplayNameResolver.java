package com.example.authsvc.infrastructure.security.jwt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link UserDisplayNameResolver}: always returns an empty string.
 * Backed off by any host-supplied {@code UserDisplayNameResolver} bean.
 */
@Component
@ConditionalOnMissingBean(UserDisplayNameResolver.class)
public class NoOpUserDisplayNameResolver implements UserDisplayNameResolver {

    @Override
    public String resolve(UUID userId) {
        return "";
    }
}
