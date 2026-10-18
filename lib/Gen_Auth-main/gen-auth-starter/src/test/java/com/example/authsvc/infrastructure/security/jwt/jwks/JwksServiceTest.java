package com.example.authsvc.infrastructure.security.jwt.jwks;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwksServiceTest {

    private static RSAPublicKey generatePublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return (RSAPublicKey) pair.getPublic();
    }

    @Test
    void reflectsKeyAddedToRegistryAfterConstruction() throws Exception {
        Map<String, JwtKeyEntry> initial = new LinkedHashMap<>();
        initial.put("v1", new JwtKeyEntry("v1", null, generatePublicKey(), null));
        JwtKeyRegistry registry = new JwtKeyRegistry(initial, "v1");
        JwksService service = new JwksService(registry);

        assertEquals(1, keyCount(service));

        registry.addKey(new JwtKeyEntry("v2", null, generatePublicKey(), null));

        assertEquals(2, keyCount(service));
    }

    @SuppressWarnings("unchecked")
    private static int keyCount(JwksService service) {
        return ((List<Object>) service.toJsonObject().get("keys")).size();
    }
}
