package com.example.authsvc.infrastructure.security.jwt.jwks;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Builds and exposes the JWKS (JSON Web Key Set) for this service.
 *
 * <p>All public keys from the {@link JwtKeyRegistry} are included so that
 * downstream services can verify tokens signed by any key that is still within
 * its grace-period overlap window.  Private keys are never referenced here.
 *
 * <p>The key set is rebuilt from the registry on every call (cheap — a handful of
 * RSA keys at most) so that runtime rotation via {@link JwtKeyRotationService}
 * is reflected immediately, with no separate cache-invalidation step.
 *
 * <h3>Rotation procedure</h3>
 * <ol>
 *   <li><b>Runtime (local mode):</b> {@code POST /internal/auth/keys/rotate} — takes
 *       effect immediately, no restart.</li>
 *   <li><b>YAML-based (any mode):</b> add the new key entry to {@code jwt.keys}, set
 *       {@code jwt.active-kid}, restart. After the old TTL + buffer, remove the old
 *       key from {@code jwt.keys} and restart again.</li>
 * </ol>
 */
@Service
public class JwksService {

    private final JwtKeyRegistry registry;

    public JwksService(JwtKeyRegistry registry) {
        this.registry = registry;
    }

    /**
     * Returns the JWKS as a standard JSON object map.
     * Callers should serialise this directly to the HTTP response.
     */
    public Map<String, Object> toJsonObject() {
        List<RSAKey> rsaKeys = registry.allEntries().stream()
                .map(entry -> new RSAKey.Builder(entry.publicKey())
                        .keyID(entry.kid())
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(new Algorithm("RS256"))
                        .build())
                .toList();

        // RSAKey extends JWK but List<RSAKey> is not a subtype of List<JWK> (generics invariance);
        // widen explicitly so the JWKSet(List<JWK>) constructor is satisfied.
        List<JWK> jwks = rsaKeys.stream().map(k -> (JWK) k).toList();

        return new JWKSet(jwks).toJSONObject();
    }
}
