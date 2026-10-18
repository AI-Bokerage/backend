package com.example.authsvc.infrastructure.security.jwt;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoOpUserDisplayNameResolverTest {

    private final UserDisplayNameResolver resolver = new NoOpUserDisplayNameResolver();

    @Test
    void alwaysReturnsEmptyString() {
        assertEquals("", resolver.resolve(UUID.randomUUID()));
    }
}
