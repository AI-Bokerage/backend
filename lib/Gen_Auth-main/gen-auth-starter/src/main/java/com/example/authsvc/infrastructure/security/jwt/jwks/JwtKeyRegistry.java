package com.example.authsvc.infrastructure.security.jwt.jwks;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory registry of all loaded RSA key pairs, keyed by {@code kid}.
 *
 * <h3>Restart-based rotation (YAML {@code jwt.keys})</h3>
 * <ol>
 *   <li>Add a new key entry to {@code jwt.keys} in YAML.</li>
 *   <li>Set {@code jwt.active-kid} to the new key's kid.</li>
 *   <li>Rolling-restart the service.  JWKS now exposes <em>both</em> public keys,
 *       so downstream validators accept tokens signed by either key.</li>
 *   <li>After the old access-token TTL + safety buffer has elapsed, remove the
 *       old key entry from {@code jwt.keys} and restart again.</li>
 * </ol>
 *
 * <h3>Runtime rotation ({@code jwt.signing-mode=local})</h3>
 * {@link JwtKeyRotationService} calls {@link #addKey}/{@link #setActiveKid}/{@link #removeKey}
 * directly on the live registry after persisting the change to Postgres, so rotation and
 * retirement take effect immediately — no restart needed.
 *
 * <p>Only one key is ever used to <em>sign</em> new tokens; all entries remain in
 * JWKS so that tokens signed before a rotation remain verifiable.
 *
 * <p>Instances are created exclusively by
 * {@link com.example.authsvc.infrastructure.security.config.RsaKeyConfig#jwtKeyRegistry}.
 */
public class JwtKeyRegistry {

    /** All loaded key entries, keyed by kid. Mutable — see the runtime rotation methods below. */
    private final Map<String, JwtKeyEntry> entries;

    /** The kid that will be embedded in new JWT headers and used for signing. */
    private final AtomicReference<String> activeKid;

    public JwtKeyRegistry(Map<String, JwtKeyEntry> entries, String activeKid) {
        this.entries   = new ConcurrentHashMap<>(entries);
        this.activeKid = new AtomicReference<>(activeKid);
    }

    // ─── Signing helpers ──────────────────────────────────────────────────────

    /** Returns the kid that new JWTs must embed in their header. */
    public String getActiveKid() {
        return activeKid.get();
    }

    /**
     * Returns the private key for the given {@code kid}, or {@code null} in KMS mode
     * where private keys are never stored locally.
     */
    public RSAPrivateKey getPrivateKey(String kid) {
        JwtKeyEntry entry = entries.get(kid);
        return entry != null ? entry.privateKey() : null;
    }

    /**
     * Returns the KMS key ID (ARN / alias) for the given {@code kid}, or {@code null}
     * in local mode where KMS is not used.
     */
    public String getKmsKeyId(String kid) {
        JwtKeyEntry entry = entries.get(kid);
        return entry != null ? entry.kmsKeyId() : null;
    }

    // ─── Verification helpers ─────────────────────────────────────────────────

    /**
     * Returns the public key for the given {@code kid}, or {@code null} when no
     * entry is registered for that kid. A {@code null} return must be treated as
     * an unrecognised token by the caller (JWT validation should fail).
     */
    public RSAPublicKey getPublicKey(String kid) {
        JwtKeyEntry entry = entries.get(kid);
        return entry != null ? entry.publicKey() : null;
    }

    // ─── JWKS helper ──────────────────────────────────────────────────────────

    /**
     * Returns all registered entries so that {@link JwksService} can expose every
     * public key in the {@code /.well-known/jwks.json} response.
     */
    public Collection<JwtKeyEntry> allEntries() {
        return entries.values();
    }

    // ─── Runtime mutation (used by JwtKeyRotationService) ─────────────────────

    /**
     * Adds a new key entry to the live registry. Does not change the active kid —
     * call {@link #setActiveKid(String)} separately to promote it.
     *
     * @throws IllegalStateException if a key with the same kid is already registered
     */
    public synchronized void addKey(JwtKeyEntry entry) {
        JwtKeyEntry existing = entries.putIfAbsent(entry.kid(), entry);
        if (existing != null) {
            throw new IllegalStateException("Duplicate kid '%s' already in registry".formatted(entry.kid()));
        }
    }

    /**
     * Promotes {@code kid} to be the active signing key.
     *
     * @throws IllegalStateException if {@code kid} is not a registered key
     */
    public synchronized void setActiveKid(String kid) {
        if (!entries.containsKey(kid)) {
            throw new IllegalStateException("Cannot activate unknown kid '%s'".formatted(kid));
        }
        activeKid.set(kid);
    }

    /**
     * Removes a key entry from the live registry.
     *
     * @throws IllegalStateException if {@code kid} is the currently active signing key
     */
    public synchronized void removeKey(String kid) {
        if (kid.equals(activeKid.get())) {
            throw new IllegalStateException("Cannot remove the active signing kid '%s'".formatted(kid));
        }
        entries.remove(kid);
    }
}
