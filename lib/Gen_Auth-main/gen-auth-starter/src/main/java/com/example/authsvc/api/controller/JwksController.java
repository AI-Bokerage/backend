package com.example.authsvc.api.controller;

import com.example.authsvc.infrastructure.security.jwt.jwks.JwksService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the public RSA key as a JSON Web Key Set (JWKS).
 *
 * <p>Other microservices can fetch this endpoint to obtain the public key
 * and verify JWTs issued by auth-svc without sharing any secrets.
 *
 * <p>The endpoint is publicly accessible (no authentication required).
 * It exposes ONLY the public key — the private key is never included.
 */
@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
public class JwksController {

    private final JwksService jwksService;

    /**
     * Returns the JWKS document containing the active public RSA key.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "keys": [{
     *     "kty": "RSA",
     *     "use": "sig",
     *     "kid": "auth-key-v1",
     *     "alg": "RS256",
     *     "n": "...",
     *     "e": "AQAB"
     *   }]
     * }
     * </pre>
     */
    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return jwksService.toJsonObject();
    }
}
