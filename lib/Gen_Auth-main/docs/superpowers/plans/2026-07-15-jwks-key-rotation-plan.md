# Operational JWKS Multi-Key Rotation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add runtime JWT signing-key rotation and retirement (no restart required) on top of the existing multi-key registry, for `jwt.signing-mode=local` deployments.

**Architecture:** A new Postgres table (`jwt_signing_key` + singleton `jwt_active_signing_key`) holds runtime-rotated keys, purely additive to the existing YAML `jwt.keys` configuration. `JwtKeyRegistry` becomes a thread-safe mutable registry so a rotation/retirement takes effect immediately. Two new internal endpoints (`/internal/auth/keys/rotate`, `/internal/auth/keys/{kid}/retire`) plus a listing endpoint, all gated by the existing `InternalTokenAuthFilter`.

**Tech Stack:** Spring Boot 4.0.6, Spring Data JPA, Flyway, `javax.crypto` (AES-256-GCM), JUnit 5, Mockito.

## Global Constraints

- Local signing mode (`jwt.signing-mode=local`) only. `rotate()` throws if called in `kms` mode.
- Rotated keys persist to Postgres (survive restart) — purely additive to existing YAML `jwt.keys`; that path is untouched.
- Old-key retirement is an explicit operator call (`POST /internal/auth/keys/{kid}/retire`), never automatic/scheduled.
- New required env var `JWT_KEY_ENCRYPTION_SECRET`: 32-byte value, base64-encoded, used for AES-256-GCM encryption of private keys at rest. Fails fast at startup if missing/wrong length (same pattern as `INTERNAL_API_SECRET`).
- New endpoints live under `/internal/auth/keys/**` — already covered by the existing `InternalTokenAuthFilter` (`X-Internal-Secret` header). No new security wiring.
- Retiring a kid that isn't in the DB (e.g. a YAML-sourced key) returns 404 — that path still uses the documented restart procedure. Retiring the current active kid returns 409.
- DB writes must commit before the in-memory `JwtKeyRegistry` is mutated — a failed transaction must never leave the registry out of sync with what a restart would reload.

---

### Task 1: Migration + entities + repositories

**Files:**
- Create: `src/main/resources/db/migration/V3__jwt_signing_keys.sql`
- Create: `src/main/java/com/example/authsvc/infrastructure/persistence/entity/JwtSigningKeyEntity.java`
- Create: `src/main/java/com/example/authsvc/infrastructure/persistence/entity/JwtActiveSigningKeyEntity.java`
- Create: `src/main/java/com/example/authsvc/infrastructure/persistence/repository/JwtSigningKeyJpaRepository.java`
- Create: `src/main/java/com/example/authsvc/infrastructure/persistence/repository/JwtActiveSigningKeyJpaRepository.java`

**Interfaces:**
- Produces: `JwtSigningKeyEntity` (`kid: String`, `privateKeyCiphertext: byte[]`, `publicKeyPem: String`, `createdAt: Instant`), `JwtActiveSigningKeyEntity` (`id: short`, `kid: String`), `JwtSigningKeyJpaRepository extends JpaRepository<JwtSigningKeyEntity, String>`, `JwtActiveSigningKeyJpaRepository extends JpaRepository<JwtActiveSigningKeyEntity, Short>` with `findSingleton(): Optional<JwtActiveSigningKeyEntity>` — all consumed by Task 4's `JwtKeyRotationService` and Task 6's `RsaKeyConfig`.

This is thin persistence plumbing (entities + repository interfaces, no branching logic) — not test-first, matches the existing repo-layer convention in this codebase.

- [ ] **Step 1: Write the migration**

```sql
-- V3__jwt_signing_keys.sql
--
-- Runtime-rotated JWT signing keys (jwt.signing-mode=local only), purely
-- additive to the existing jwt.keys YAML configuration — those keys are
-- never written here. jwt_active_signing_key is a one-row singleton
-- (id=1) tracking which DB-sourced kid, if any, is the active signer.

CREATE TABLE IF NOT EXISTS jwt_signing_key (
    kid                    TEXT                     PRIMARY KEY,
    private_key_ciphertext BYTEA                    NOT NULL,
    public_key_pem         TEXT                     NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS jwt_active_signing_key (
    id  SMALLINT PRIMARY KEY CHECK (id = 1),
    kid TEXT NOT NULL REFERENCES jwt_signing_key(kid)
);
```

- [ ] **Step 2: Write the entities**

```java
// src/main/java/com/example/authsvc/infrastructure/persistence/entity/JwtSigningKeyEntity.java
package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jwt_signing_key")
public class JwtSigningKeyEntity {

    @Id
    @Column(name = "kid", updatable = false, nullable = false)
    private String kid;

    @Lob
    @Column(name = "private_key_ciphertext", nullable = false)
    private byte[] privateKeyCiphertext;

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

```java
// src/main/java/com/example/authsvc/infrastructure/persistence/entity/JwtActiveSigningKeyEntity.java
package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jwt_active_signing_key")
public class JwtActiveSigningKeyEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private short id;

    @Column(name = "kid", nullable = false)
    private String kid;
}
```

- [ ] **Step 3: Write the repositories**

```java
// src/main/java/com/example/authsvc/infrastructure/persistence/repository/JwtSigningKeyJpaRepository.java
package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.JwtSigningKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JwtSigningKeyJpaRepository extends JpaRepository<JwtSigningKeyEntity, String> {
}
```

```java
// src/main/java/com/example/authsvc/infrastructure/persistence/repository/JwtActiveSigningKeyJpaRepository.java
package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.JwtActiveSigningKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JwtActiveSigningKeyJpaRepository extends JpaRepository<JwtActiveSigningKeyEntity, Short> {

    /** The one row (id=1) tracking the current DB-sourced active signing kid, if any. */
    default Optional<JwtActiveSigningKeyEntity> findSingleton() {
        return findById((short) 1);
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew.bat compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V3__jwt_signing_keys.sql src/main/java/com/example/authsvc/infrastructure/persistence/entity/JwtSigningKeyEntity.java src/main/java/com/example/authsvc/infrastructure/persistence/entity/JwtActiveSigningKeyEntity.java src/main/java/com/example/authsvc/infrastructure/persistence/repository/JwtSigningKeyJpaRepository.java src/main/java/com/example/authsvc/infrastructure/persistence/repository/JwtActiveSigningKeyJpaRepository.java
git commit -m "add jwt_signing_key/jwt_active_signing_key tables and repositories"
```

---

### Task 2: Key encryption at rest

**Files:**
- Create: `src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/KeyEncryptionUtil.java`
- Test: `src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/KeyEncryptionUtilTest.java`
- Modify: `src/main/resources/application.yaml`

**Interfaces:**
- Produces: `KeyEncryptionUtil(String base64Secret)` (constructor, throws `IllegalStateException` on bad secret), `encrypt(byte[] plaintext): byte[]`, `decrypt(byte[] ciphertext): byte[]` — consumed by Task 4's `JwtKeyRotationService` and Task 6's `RsaKeyConfig`.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/KeyEncryptionUtilTest.java
package com.example.authsvc.infrastructure.security.jwt.jwks;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyEncryptionUtilTest {

    private static String validSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void roundTripsPlaintext() {
        KeyEncryptionUtil util = new KeyEncryptionUtil(validSecret());
        byte[] plaintext = "super-secret-private-key-bytes".getBytes();

        byte[] ciphertext = util.encrypt(plaintext);
        byte[] decrypted  = util.decrypt(ciphertext);

        assertArrayEquals(plaintext, decrypted);
        assertNotEquals(new String(plaintext), new String(ciphertext));
    }

    @Test
    void rejectsWrongLengthSecret() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new KeyEncryptionUtil(tooShort));
    }

    @Test
    void rejectsNonBase64Secret() {
        assertThrows(IllegalStateException.class, () -> new KeyEncryptionUtil("not-valid-base64!!!"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat test --tests "*.KeyEncryptionUtilTest"`
Expected: FAIL with "cannot find symbol: class KeyEncryptionUtil"

- [ ] **Step 3: Write minimal implementation**

```java
// src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/KeyEncryptionUtil.java
package com.example.authsvc.infrastructure.security.jwt.jwks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for RSA private keys persisted by {@link JwtKeyRotationService}.
 *
 * <p>Ciphertext layout is {@code [12-byte IV][GCM ciphertext+tag]} concatenated into a
 * single byte array — the IV must travel with the ciphertext since decryption needs it.
 */
@Component
public class KeyEncryptionUtil {

    private static final String AES_GCM  = "AES/GCM/NoPadding";
    private static final int    IV_BYTES = 12;
    private static final int    TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom  random = new SecureRandom();

    public KeyEncryptionUtil(@Value("${jwt.key-encryption-secret}") String base64Secret) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.key-encryption-secret must be valid base64", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "jwt.key-encryption-secret must decode to exactly 32 bytes (AES-256), got " + decoded.length);
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_BYTES, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt private key", e);
        }
    }

    public byte[] decrypt(byte[] ivAndCiphertext) {
        try {
            byte[] iv         = Arrays.copyOfRange(ivAndCiphertext, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(ivAndCiphertext, IV_BYTES, ivAndCiphertext.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt private key", e);
        }
    }
}
```

- [ ] **Step 4: Add the property to application.yaml**

Add this line inside the existing top-level `jwt:` block (after the `keys:` section, e.g. after line 270 `public-key-path: classpath:keys/public.pem`):

```yaml
  key-encryption-secret: ${JWT_KEY_ENCRYPTION_SECRET}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat test --tests "*.KeyEncryptionUtilTest"`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/KeyEncryptionUtil.java src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/KeyEncryptionUtilTest.java src/main/resources/application.yaml
git commit -m "add AES-256-GCM encryption for rotated JWT private keys at rest"
```

---

### Task 3: Mutable JwtKeyRegistry

**Files:**
- Modify: `src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRegistry.java` (full file replacement below)
- Test: `src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRegistryTest.java`

**Interfaces:**
- Produces (new, in addition to the existing `getActiveKid`/`getPrivateKey`/`getKmsKeyId`/`getPublicKey`/`allEntries`): `addKey(JwtKeyEntry entry): void` (throws `IllegalStateException` on duplicate kid), `setActiveKid(String kid): void` (throws `IllegalStateException` on unknown kid), `removeKey(String kid): void` (throws `IllegalStateException` if `kid` is the active kid) — consumed by Task 4's `JwtKeyRotationService`.
- No change to existing signatures — `RsaKeyConfig`'s constructor call `new JwtKeyRegistry(registry, activeKid)` (a `Map<String, JwtKeyEntry>` and a `String`) still works unchanged.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRegistryTest.java
package com.example.authsvc.infrastructure.security.jwt.jwks;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtKeyRegistryTest {

    private static JwtKeyEntry entry(String kid) {
        return new JwtKeyEntry(kid, null, null, null);
    }

    private static JwtKeyRegistry registryWith(String... kids) {
        Map<String, JwtKeyEntry> map = new LinkedHashMap<>();
        for (String kid : kids) {
            map.put(kid, entry(kid));
        }
        return new JwtKeyRegistry(map, kids[0]);
    }

    @Test
    void addKeyMakesItRetrievableButNotActive() {
        JwtKeyRegistry registry = registryWith("v1");

        registry.addKey(entry("v2"));

        assertEquals("v1", registry.getActiveKid());
        assertEquals(2, registry.allEntries().size());
    }

    @Test
    void addKeyRejectsDuplicateKid() {
        JwtKeyRegistry registry = registryWith("v1");

        assertThrows(IllegalStateException.class, () -> registry.addKey(entry("v1")));
    }

    @Test
    void setActiveKidPromotesRegisteredKey() {
        JwtKeyRegistry registry = registryWith("v1");
        registry.addKey(entry("v2"));

        registry.setActiveKid("v2");

        assertEquals("v2", registry.getActiveKid());
    }

    @Test
    void setActiveKidRejectsUnknownKid() {
        JwtKeyRegistry registry = registryWith("v1");

        assertThrows(IllegalStateException.class, () -> registry.setActiveKid("nope"));
    }

    @Test
    void removeKeyDropsNonActiveEntry() {
        JwtKeyRegistry registry = registryWith("v1");
        registry.addKey(entry("v2"));

        registry.removeKey("v2");

        assertNull(registry.getPrivateKey("v2"));
        assertEquals(1, registry.allEntries().size());
    }

    @Test
    void removeKeyRejectsActiveKid() {
        JwtKeyRegistry registry = registryWith("v1");

        assertThrows(IllegalStateException.class, () -> registry.removeKey("v1"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat test --tests "*.JwtKeyRegistryTest"`
Expected: FAIL with "cannot find symbol: method addKey" (etc.)

- [ ] **Step 3: Replace the full implementation**

```java
// src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRegistry.java — full file after this change
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
    public void addKey(JwtKeyEntry entry) {
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
    public void setActiveKid(String kid) {
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
    public void removeKey(String kid) {
        if (kid.equals(activeKid.get())) {
            throw new IllegalStateException("Cannot remove the active signing kid '%s'".formatted(kid));
        }
        entries.remove(kid);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat test --tests "*.JwtKeyRegistryTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Run the full test suite to confirm no regression**

Run: `./gradlew.bat test`
Expected: all existing tests still PASS (no other code touches `JwtKeyRegistry`'s constructor or field types)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRegistry.java src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRegistryTest.java
git commit -m "make JwtKeyRegistry mutable for runtime key rotation"
```

---

### Task 4: JwtKeyRotationService

**Files:**
- Create: `src/main/java/com/example/authsvc/common/exception/KeyNotFoundException.java`
- Create: `src/main/java/com/example/authsvc/common/exception/ActiveKeyRetirementException.java`
- Create: `src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRotationService.java`
- Test: `src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRotationServiceTest.java`

**Interfaces:**
- Consumes: `JwtSigningKeyJpaRepository`, `JwtActiveSigningKeyJpaRepository` (Task 1), `KeyEncryptionUtil` (Task 2), `JwtKeyRegistry.addKey`/`setActiveKid`/`removeKey`/`getActiveKid` (Task 3), `JwtProperties.getSigningMode()` (existing, `src/main/java/com/example/authsvc/config/properties/JwtProperties.java`), `PlatformTransactionManager` (Spring, existing).
- Produces: `JwtKeyRotationService.RotatedKey` (`kid: String`, `publicKeyPem: String`), `rotate(): RotatedKey` (throws `UnsupportedOperationException` if `jwt.signing-mode` is not `local` — Global Constraint), `retire(String kid): void` — consumed by Task 5's `JwksAdminController`.

- [ ] **Step 1: Write the two new exceptions**

```java
// src/main/java/com/example/authsvc/common/exception/KeyNotFoundException.java
package com.example.authsvc.common.exception;

public class KeyNotFoundException extends RuntimeException {
    public KeyNotFoundException(String message) {
        super(message);
    }
}
```

```java
// src/main/java/com/example/authsvc/common/exception/ActiveKeyRetirementException.java
package com.example.authsvc.common.exception;

public class ActiveKeyRetirementException extends RuntimeException {
    public ActiveKeyRetirementException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Write the failing tests**

```java
// src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRotationServiceTest.java
package com.example.authsvc.infrastructure.security.jwt.jwks;

import com.example.authsvc.common.exception.ActiveKeyRetirementException;
import com.example.authsvc.common.exception.KeyNotFoundException;
import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.infrastructure.persistence.entity.JwtSigningKeyEntity;
import com.example.authsvc.infrastructure.persistence.repository.JwtActiveSigningKeyJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.JwtSigningKeyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtKeyRotationServiceTest {

    @Mock private JwtSigningKeyJpaRepository       signingKeyRepository;
    @Mock private JwtActiveSigningKeyJpaRepository activeKeyRepository;
    @Mock private JwtProperties                    jwtProperties;

    /** Minimal fake transaction manager: commits synchronously, counts commits. */
    private static class CountingTransactionManager implements PlatformTransactionManager {
        int commits = 0;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    private CountingTransactionManager transactionManager;
    private KeyEncryptionUtil           encryptionUtil;
    private JwtKeyRegistry              registry;
    private JwtKeyRotationService       service;

    @BeforeEach
    void setUp() {
        transactionManager = new CountingTransactionManager();
        encryptionUtil = new KeyEncryptionUtil(Base64.getEncoder().encodeToString(new byte[32]));

        Map<String, JwtKeyEntry> initial = new LinkedHashMap<>();
        initial.put("v1", new JwtKeyEntry("v1", null, null, null));
        registry = new JwtKeyRegistry(initial, "v1");

        service = new JwtKeyRotationService(
                signingKeyRepository, activeKeyRepository, encryptionUtil, registry, jwtProperties, transactionManager);
    }

    @Test
    void rotateCommitsToDbBeforeUpdatingLiveRegistry() {
        when(jwtProperties.getSigningMode()).thenReturn("local");
        when(activeKeyRepository.findSingleton()).thenReturn(Optional.empty());

        JwtKeyRotationService.RotatedKey result = service.rotate();

        assertEquals(1, transactionManager.commits);
        verify(signingKeyRepository).save(any(JwtSigningKeyEntity.class));
        verify(activeKeyRepository).save(any());
        assertEquals(result.kid(), registry.getActiveKid());
        assertTrue(result.publicKeyPem().startsWith("-----BEGIN PUBLIC KEY-----"));
    }

    @Test
    void rotateThrowsInKmsMode() {
        when(jwtProperties.getSigningMode()).thenReturn("kms");

        assertThrows(UnsupportedOperationException.class, () -> service.rotate());
        verifyNoInteractions(signingKeyRepository);
    }

    @Test
    void retireRemovesDbRowAndRegistryEntryForNonActiveKid() {
        registry.addKey(new JwtKeyEntry("v2", null, null, null));
        JwtSigningKeyEntity entity = JwtSigningKeyEntity.builder().kid("v2").build();
        when(signingKeyRepository.findById("v2")).thenReturn(Optional.of(entity));

        service.retire("v2");

        verify(signingKeyRepository).delete(entity);
        assertEquals(1, transactionManager.commits);
    }

    @Test
    void retireRejectsUnknownKid() {
        when(signingKeyRepository.findById("nope")).thenReturn(Optional.empty());

        assertThrows(KeyNotFoundException.class, () -> service.retire("nope"));
    }

    @Test
    void retireRejectsActiveKid() {
        JwtSigningKeyEntity entity = JwtSigningKeyEntity.builder().kid("v1").build();
        when(signingKeyRepository.findById("v1")).thenReturn(Optional.of(entity));

        assertThrows(ActiveKeyRetirementException.class, () -> service.retire("v1"));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew.bat test --tests "*.JwtKeyRotationServiceTest"`
Expected: FAIL with "cannot find symbol: class JwtKeyRotationService"

- [ ] **Step 4: Write minimal implementation**

```java
// src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRotationService.java
package com.example.authsvc.infrastructure.security.jwt.jwks;

import com.example.authsvc.common.exception.ActiveKeyRetirementException;
import com.example.authsvc.common.exception.KeyNotFoundException;
import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.infrastructure.persistence.entity.JwtActiveSigningKeyEntity;
import com.example.authsvc.infrastructure.persistence.entity.JwtSigningKeyEntity;
import com.example.authsvc.infrastructure.persistence.repository.JwtActiveSigningKeyJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.JwtSigningKeyJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;

/**
 * Runtime rotation and retirement of RSA signing keys ({@code jwt.signing-mode=local} only).
 *
 * <p>Each rotation persists the new keypair to Postgres <em>inside a committed transaction
 * before</em> updating the live {@link JwtKeyRegistry} — a DB failure never leaves the
 * in-memory registry out of sync with what would be reloaded on the next restart. A plain
 * {@code @Transactional} method would commit only after the method returns (proxy-based),
 * which is too late for that guarantee — {@link TransactionTemplate} is used explicitly so
 * the commit happens strictly before the in-memory mutation below it.
 */
@Slf4j
@Service
public class JwtKeyRotationService {

    private static final int   RSA_KEY_BITS  = 2048;
    private static final short ACTIVE_ROW_ID = 1;

    private final JwtSigningKeyJpaRepository       signingKeyRepository;
    private final JwtActiveSigningKeyJpaRepository activeKeyRepository;
    private final KeyEncryptionUtil                encryptionUtil;
    private final JwtKeyRegistry                   registry;
    private final JwtProperties                    jwtProperties;
    private final TransactionTemplate              transactionTemplate;

    public JwtKeyRotationService(JwtSigningKeyJpaRepository signingKeyRepository,
                                 JwtActiveSigningKeyJpaRepository activeKeyRepository,
                                 KeyEncryptionUtil encryptionUtil,
                                 JwtKeyRegistry registry,
                                 JwtProperties jwtProperties,
                                 PlatformTransactionManager transactionManager) {
        this.signingKeyRepository = signingKeyRepository;
        this.activeKeyRepository  = activeKeyRepository;
        this.encryptionUtil       = encryptionUtil;
        this.registry             = registry;
        this.jwtProperties        = jwtProperties;
        this.transactionTemplate  = new TransactionTemplate(transactionManager);
    }

    /**
     * Generates a new RSA keypair, persists it, promotes it to active, and pushes it into the
     * live registry. Returns the new kid and its public key PEM (never the private key).
     *
     * @throws UnsupportedOperationException if {@code jwt.signing-mode} is not {@code local} —
     *                                        creating a new AWS KMS key is an AWS-side
     *                                        provisioning action, out of scope for this endpoint
     */
    public RotatedKey rotate() {
        if (!"local".equalsIgnoreCase(jwtProperties.getSigningMode())) {
            throw new UnsupportedOperationException(
                    "JWT key rotation via this endpoint only supports jwt.signing-mode=local");
        }
        String  kid     = "auth-key-" + UUID.randomUUID();
        KeyPair keyPair = generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey  publicKey  = (RSAPublicKey)  keyPair.getPublic();
        String publicKeyPem = encodePublicKeyPem(publicKey);

        transactionTemplate.executeWithoutResult(status -> {
            signingKeyRepository.save(JwtSigningKeyEntity.builder()
                    .kid(kid)
                    .privateKeyCiphertext(encryptionUtil.encrypt(privateKey.getEncoded()))
                    .publicKeyPem(publicKeyPem)
                    .build());

            JwtActiveSigningKeyEntity activeRow = activeKeyRepository.findSingleton()
                    .orElseGet(() -> new JwtActiveSigningKeyEntity(ACTIVE_ROW_ID, kid));
            activeRow.setKid(kid);
            activeKeyRepository.save(activeRow);
        });

        registry.addKey(new JwtKeyEntry(kid, privateKey, publicKey, null));
        registry.setActiveKid(kid);

        log.info("jwks.rotation.completed kid={}", kid);
        return new RotatedKey(kid, publicKeyPem);
    }

    /**
     * Removes a DB-persisted key from Postgres and the live registry.
     *
     * @throws KeyNotFoundException         if {@code kid} has no matching row (includes
     *                                       YAML-sourced keys — those aren't managed here)
     * @throws ActiveKeyRetirementException if {@code kid} is the current active signer
     */
    public void retire(String kid) {
        JwtSigningKeyEntity entity = signingKeyRepository.findById(kid)
                .orElseThrow(() -> new KeyNotFoundException("No rotated key found for kid '%s'".formatted(kid)));
        if (kid.equals(registry.getActiveKid())) {
            throw new ActiveKeyRetirementException(
                    "Cannot retire the active signing kid '%s' — rotate first".formatted(kid));
        }

        transactionTemplate.executeWithoutResult(status -> signingKeyRepository.delete(entity));
        registry.removeKey(kid);

        log.info("jwks.retirement.completed kid={}", kid);
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    private String encodePublicKeyPem(RSAPublicKey publicKey) {
        String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        StringBuilder pem = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        pem.append("-----END PUBLIC KEY-----\n");
        return pem.toString();
    }

    public record RotatedKey(String kid, String publicKeyPem) {}
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat test --tests "*.JwtKeyRotationServiceTest"`
Expected: PASS (5 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/authsvc/common/exception/KeyNotFoundException.java src/main/java/com/example/authsvc/common/exception/ActiveKeyRetirementException.java src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRotationService.java src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRotationServiceTest.java
git commit -m "add JwtKeyRotationService: rotate/retire local-mode signing keys"
```

---

### Task 5: Internal endpoints + exception wiring

**Files:**
- Modify: `src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java`
- Create: `src/main/java/com/example/authsvc/api/dto/response/RotateKeyResponse.java`
- Create: `src/main/java/com/example/authsvc/api/dto/response/KeyListResponse.java`
- Create: `src/main/java/com/example/authsvc/api/controller/JwksAdminController.java`
- Test: `src/test/java/com/example/authsvc/api/controller/JwksAdminControllerTest.java`

**Interfaces:**
- Consumes: `JwtKeyRotationService.rotate`/`retire` (Task 4), `JwtKeyRegistry.allEntries`/`getActiveKid` (Task 3), `KeyNotFoundException`/`ActiveKeyRetirementException` (Task 4).
- Produces: `RotateKeyResponse` (`kid: String`, `publicKeyPem: String`), `KeyListResponse` (`activeKid: String`, `kids: List<String>`), `JwksAdminController` (Spring `RestController`), mounted at `/internal/auth/keys` — already covered by the existing `InternalTokenAuthFilter`, no change needed to `SecurityConfig` (`/internal/**` is already `permitAll()` there).

- [ ] **Step 1: Add the two exception handlers**

In `src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java`, add these two imports next to the existing `com.example.authsvc.common.exception.*` imports:

```java
import com.example.authsvc.common.exception.ActiveKeyRetirementException;
import com.example.authsvc.common.exception.KeyNotFoundException;
```

Add these two handler methods (anywhere among the other `@ExceptionHandler` methods, e.g. right after `handleRegistrationDisabled`):

```java
    @ExceptionHandler(KeyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleKeyNotFound(KeyNotFoundException ex,
                                                            HttpServletRequest request) {
        log.info("jwks.key_not_found path={} message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ActiveKeyRetirementException.class)
    public ResponseEntity<ErrorResponse> handleActiveKeyRetirement(ActiveKeyRetirementException ex,
                                                                    HttpServletRequest request) {
        log.info("jwks.active_key_retirement_rejected path={} message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }
```

- [ ] **Step 2: Write the response DTOs**

```java
// src/main/java/com/example/authsvc/api/dto/response/RotateKeyResponse.java
package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response body returned after a successful JWT signing-key rotation")
public record RotateKeyResponse(
        @Schema(description = "The new key's kid, now the active signer")
        String kid,
        @Schema(description = "The new key's public key in PEM format")
        String publicKeyPem
) {}
```

```java
// src/main/java/com/example/authsvc/api/dto/response/KeyListResponse.java
package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "All kids currently in the JWT signing key registry")
public record KeyListResponse(
        @Schema(description = "The kid currently used to sign new tokens")
        String activeKid,
        @Schema(description = "Every kid in the registry, including retired-but-not-yet-removed ones")
        List<String> kids
) {}
```

- [ ] **Step 3: Write the failing controller test**

```java
// src/test/java/com/example/authsvc/api/controller/JwksAdminControllerTest.java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.response.KeyListResponse;
import com.example.authsvc.api.dto.response.RotateKeyResponse;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyEntry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRotationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwksAdminControllerTest {

    @Mock private JwtKeyRotationService rotationService;

    private JwtKeyRegistry      registry;
    private JwksAdminController controller;

    @BeforeEach
    void setUp() {
        Map<String, JwtKeyEntry> initial = new LinkedHashMap<>();
        initial.put("v1", new JwtKeyEntry("v1", null, null, null));
        registry = new JwtKeyRegistry(initial, "v1");
        controller = new JwksAdminController(rotationService, registry);
    }

    @Test
    void rotateReturnsNewKidAndPublicKeyPem() {
        when(rotationService.rotate())
                .thenReturn(new JwtKeyRotationService.RotatedKey("v2", "-----BEGIN PUBLIC KEY-----\n...\n"));

        ResponseEntity<RotateKeyResponse> response = controller.rotate();

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("v2", response.getBody().kid());
    }

    @Test
    void retireDelegatesToServiceAndReturns204() {
        ResponseEntity<Void> response = controller.retire("v2");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(rotationService).retire("v2");
    }

    @Test
    void listReturnsActiveKidAndAllKids() {
        ResponseEntity<KeyListResponse> response = controller.list();

        assertEquals("v1", response.getBody().activeKid());
        assertEquals(1, response.getBody().kids().size());
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew.bat test --tests "*.JwksAdminControllerTest"`
Expected: FAIL with "cannot find symbol: class JwksAdminController"

- [ ] **Step 5: Write the controller**

```java
// src/main/java/com/example/authsvc/api/controller/JwksAdminController.java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.response.KeyListResponse;
import com.example.authsvc.api.dto.response.RotateKeyResponse;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyEntry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoints for runtime JWT signing-key rotation ({@code jwt.signing-mode=local} only).
 *
 * <p>Protected by {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter} —
 * callers must include {@code X-Internal-Secret: <INTERNAL_SERVICE_SECRET>} header.
 */
@Slf4j
@RestController
@RequestMapping("/internal/auth/keys")
@RequiredArgsConstructor
public class JwksAdminController {

    private final JwtKeyRotationService rotationService;
    private final JwtKeyRegistry        registry;

    @PostMapping("/rotate")
    public ResponseEntity<RotateKeyResponse> rotate() {
        JwtKeyRotationService.RotatedKey result = rotationService.rotate();
        log.info("internal.jwks.rotated kid={}", result.kid());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RotateKeyResponse(result.kid(), result.publicKeyPem()));
    }

    @PostMapping("/{kid}/retire")
    public ResponseEntity<Void> retire(@PathVariable String kid) {
        rotationService.retire(kid);
        log.info("internal.jwks.retired kid={}", kid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<KeyListResponse> list() {
        List<String> kids = registry.allEntries().stream()
                .map(JwtKeyEntry::kid)
                .toList();
        return ResponseEntity.ok(new KeyListResponse(registry.getActiveKid(), kids));
    }
}
```

- [ ] **Step 6: Run test to verify it passes, then the full suite**

Run: `./gradlew.bat test`
Expected: all PASS, including the 3 new `JwksAdminControllerTest` tests

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java src/main/java/com/example/authsvc/api/dto/response/RotateKeyResponse.java src/main/java/com/example/authsvc/api/dto/response/KeyListResponse.java src/main/java/com/example/authsvc/api/controller/JwksAdminController.java src/test/java/com/example/authsvc/api/controller/JwksAdminControllerTest.java
git commit -m "add /internal/auth/keys rotate/retire/list endpoints"
```

---

### Task 6: Boot-time merge of DB-sourced keys

**Files:**
- Modify: `src/main/java/com/example/authsvc/infrastructure/security/config/RsaKeyConfig.java`

**Interfaces:**
- Consumes: `JwtSigningKeyJpaRepository.findAll` (Task 1), `JwtActiveSigningKeyJpaRepository.findSingleton` (Task 1), `KeyEncryptionUtil.decrypt` (Task 2).
- No change to `JwtKeyRegistry jwtKeyRegistry(...)`'s effect on other beans — `LocalPemJwtSigner`, `JwtUtils`, `JwksService` all consume `JwtKeyRegistry` and need no changes here (Task 7 changes `JwksService` for a different reason).

This modifies an existing `@Bean` method with no isolated unit-test seam (it's a Spring configuration class exercised by the boot-time integration test in Task 8); correctness here is verified by the full test suite plus the manual verification pass.

- [ ] **Step 1: Add the two new constructor-injected fields**

In `src/main/java/com/example/authsvc/infrastructure/security/config/RsaKeyConfig.java`, change:

```java
    private final JwtProperties jwtProperties;
    private final AwsProperties awsProperties;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
```

to:

```java
    private final JwtProperties jwtProperties;
    private final AwsProperties awsProperties;
    private final com.example.authsvc.infrastructure.persistence.repository.JwtSigningKeyJpaRepository signingKeyRepository;
    private final com.example.authsvc.infrastructure.persistence.repository.JwtActiveSigningKeyJpaRepository activeKeyRepository;
    private final com.example.authsvc.infrastructure.security.jwt.jwks.KeyEncryptionUtil encryptionUtil;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
```

(Fully-qualified inline to avoid disturbing the existing import block ordering — `@RequiredArgsConstructor` picks these up automatically since they're `final` with no initializer.)

- [ ] **Step 2: Insert the DB-key-loading block and change active-kid resolution**

Replace this block (the end of the `jwtKeyRegistry` method):

```java
        // Resolve & validate the active signing kid
        String activeKid = jwtProperties.resolvedActiveKid();
        if (activeKid == null || activeKid.isBlank()) {
            throw new IllegalStateException(
                    "No active signing kid configured — set jwt.active-kid or jwt.key-id");
        }
        if (!registry.containsKey(activeKid)) {
            throw new IllegalStateException(
                    ("jwt.active-kid '%s' not found in the key registry. " +
                     "Available kids: %s").formatted(activeKid, registry.keySet()));
        }

        log.info("jwks.registry.initialized active={} total={} signingMode={} loadMs={}",
                activeKid, registry.size(), jwtProperties.getSigningMode(),
                System.currentTimeMillis() - start);

        return new JwtKeyRegistry(registry, activeKid);
    }
```

with:

```java
        // ── Runtime-rotated keys from Postgres (local mode only, purely additive) ──
        for (var dbKey : signingKeyRepository.findAll()) {
            String kid = dbKey.getKid();
            if (registry.containsKey(kid)) {
                throw new IllegalStateException(
                        "Duplicate kid '%s' found in both jwt.keys and the jwt_signing_key table"
                                .formatted(kid));
            }
            byte[] privBytes = encryptionUtil.decrypt(dbKey.getPrivateKeyCiphertext());
            RSAPrivateKey priv = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            byte[] pubDer = decodePem(dbKey.getPublicKeyPem(), "PUBLIC KEY");
            RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(pubDer));
            validateKeypair(kid, priv, pub);
            registry.put(kid, new JwtKeyEntry(kid, priv, pub, null));
            log.info("jwks.registry.loaded kid={} mode=db-rotated", kid);
        }

        // Resolve & validate the active signing kid — a DB-tracked active kid (set by
        // JwtKeyRotationService.rotate()) takes precedence over the YAML-configured one.
        String activeKid = activeKeyRepository.findSingleton()
                .map(com.example.authsvc.infrastructure.persistence.entity.JwtActiveSigningKeyEntity::getKid)
                .orElseGet(jwtProperties::resolvedActiveKid);
        if (activeKid == null || activeKid.isBlank()) {
            throw new IllegalStateException(
                    "No active signing kid configured — set jwt.active-kid or jwt.key-id");
        }
        if (!registry.containsKey(activeKid)) {
            throw new IllegalStateException(
                    ("jwt.active-kid '%s' not found in the key registry. " +
                     "Available kids: %s").formatted(activeKid, registry.keySet()));
        }

        log.info("jwks.registry.initialized active={} total={} signingMode={} loadMs={}",
                activeKid, registry.size(), jwtProperties.getSigningMode(),
                System.currentTimeMillis() - start);

        return new JwtKeyRegistry(registry, activeKid);
    }
```

- [ ] **Step 3: Run the full test suite and typecheck**

Run: `./gradlew.bat test compileJava`
Expected: `BUILD SUCCESSFUL`, all tests pass (no existing test constructs `RsaKeyConfig` directly, so no test signatures need updating)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/authsvc/infrastructure/security/config/RsaKeyConfig.java
git commit -m "merge DB-rotated JWT signing keys into the registry at boot"
```

---

### Task 7: JwksService reflects rotation without restart

**Files:**
- Modify: `src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwksService.java`
- Test: `src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwksServiceTest.java`

**Interfaces:**
- Consumes: `JwtKeyRegistry.allEntries` (existing), `JwtKeyRegistry.addKey` (Task 3, used only by the test).
- No change to `toJsonObject(): Map<String, Object>`'s signature — callers (`JwksController`, wherever `/.well-known/jwks.json` is served) need no changes.

- [ ] **Step 1: Write the failing test**

This test would fail against the *current* implementation (which builds the `JWKSet` once in the constructor) because `service.toJsonObject()` would still show only 1 key after a key is added to the registry post-construction.

```java
// src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwksServiceTest.java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "*.JwksServiceTest"`
Expected: FAIL — second `assertEquals(2, keyCount(service))` sees `1` (the constructor-built `JWKSet` is stale)

- [ ] **Step 3: Rebuild the JWKSet on every call instead of once in the constructor**

```java
// src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwksService.java — full file after this change
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
```

- [ ] **Step 4: Run test to verify it passes, then the full suite**

Run: `./gradlew.bat test`
Expected: all PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwksService.java src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwksServiceTest.java
git commit -m "rebuild JWKS from the registry per-request so rotation is reflected immediately"
```

---

### Task 8: README + manual verification

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing new — this documents Tasks 1–7's endpoints and runs the full rotation flow against real Postgres.

- [ ] **Step 1: Update README's env var table**

Add this row to the environment variables table:

```markdown
| `JWT_KEY_ENCRYPTION_SECRET` | yes | 32-byte value, base64-encoded (`openssl rand -base64 32`). Encrypts rotated JWT private keys at rest. |
```

- [ ] **Step 2: Update README's "Running it locally" section**

Add to the `bootRun` env var list:

```bash
JWT_KEY_ENCRYPTION_SECRET=$(openssl rand -base64 32) \
```

- [ ] **Step 3: Add the new endpoints to README's endpoint table**

```markdown
| POST   | `/internal/auth/keys/rotate`     | `X-Internal-Secret` header | Generates a new RSA key, persists it, promotes it active immediately (local mode only) |
| POST   | `/internal/auth/keys/{kid}/retire` | `X-Internal-Secret` header | Removes a rotated key; 404 if unknown/YAML-sourced, 409 if it's the active key |
| GET    | `/internal/auth/keys`            | `X-Internal-Secret` header | Lists all kids in the registry and which is active |
```

- [ ] **Step 4: Update "Genericization decisions" / "Not done yet"**

Remove "JWKS multi-key rotation (the code supports it, nothing exercises rotation)" from "Not done yet", and add under "Genericization decisions":

```markdown
- **JWKS rotation**: `jwt.signing-mode=local` deployments can rotate/retire signing keys at runtime via `/internal/auth/keys/**` — no restart needed. Rotated keys persist to Postgres (`jwt_signing_key`/`jwt_active_signing_key` tables), encrypted at rest with AES-256-GCM (`JWT_KEY_ENCRYPTION_SECRET`). `kms` mode still uses the existing restart-based YAML procedure (creating a new AWS KMS key is an AWS-side action, out of scope for this endpoint).
```

- [ ] **Step 5: Actually run the manual check against real Postgres+Redis**

```bash
docker compose up -d
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC \
AUTH_DB_URL=jdbc:postgresql://localhost:5433/genauth \
AUTH_DB_USERNAME=postgres AUTH_DB_PASSWORD=postgres \
SPRING_DATA_REDIS_PORT=6380 \
INTERNAL_SERVICE_SECRET=dev-secret \
JWT_KEY_ENCRYPTION_SECRET=$(openssl rand -base64 32) \
SUPER_ADMIN_EMAIL=admin@example.com SUPER_ADMIN_PASSWORD='DevAdmin@123' \
JWT_ISSUER=genauth JWT_AUDIENCE=genauth \
MAIL_PASSWORD=dev-mail-password \
./gradlew bootRun
```

```bash
# 1. Confirm JWKS starts with exactly one key (the YAML one)
curl -s localhost:8101/.well-known/jwks.json | jq '.keys | length'
# Expected: 1

# 2. List keys via the admin endpoint
curl -s localhost:8101/internal/auth/keys -H 'X-Internal-Secret: dev-secret'
# Expected: {"activeKid":"auth-key-v1","kids":["auth-key-v1"]}

# 3. Rotate
curl -s -X POST localhost:8101/internal/auth/keys/rotate -H 'X-Internal-Secret: dev-secret'
# Expected: 201, {"kid":"auth-key-<uuid>","publicKeyPem":"-----BEGIN PUBLIC KEY-----..."}

# 4. JWKS now shows 2 keys
curl -s localhost:8101/.well-known/jwks.json | jq '.keys | length'
# Expected: 2

# 5. Log in — new token's kid header should be the rotated key
curl -s -X POST localhost:8101/api/v1/auth/login -H 'content-type: application/json' \
  -d '{"email":"admin@example.com","password":"DevAdmin@123"}' | jq -r .accessToken \
  | cut -d. -f1 | base64 -d
# Expected: {"alg":"RS256","kid":"auth-key-<uuid from step 3>"}

# 6. Retiring the now-active key must 409
curl -s -i -X POST localhost:8101/internal/auth/keys/<kid from step 3>/retire -H 'X-Internal-Secret: dev-secret'
# Expected: 409

# 7. Retiring the original YAML key must 404 (it's not DB-managed)
curl -s -i -X POST localhost:8101/internal/auth/keys/auth-key-v1/retire -H 'X-Internal-Secret: dev-secret'
# Expected: 404

# 8. Restart the app (Ctrl-C, rerun the bootRun command above), then confirm the rotated key survived
curl -s localhost:8101/internal/auth/keys -H 'X-Internal-Secret: dev-secret'
# Expected: both auth-key-v1 and the rotated kid still present, rotated kid still active
```

Expected: every step above matches its inline "Expected" note — especially step 8, proving the rotation survived a restart.

- [ ] **Step 6: Commit**

```bash
git add README.md
git commit -m "document runtime JWKS key rotation and manual verification steps"
```

## Self-Review Notes

- **Spec coverage:** local-mode-only rotation ✅ (pre-flight review caught that an earlier draft never actually checked `jwt.signing-mode` in `rotate()` despite the Global Constraint requiring it — fixed: `rotate()` now throws `UnsupportedOperationException` when signing mode isn't `local`, covered by `rotateThrowsInKmsMode`), DB persistence surviving restart ✅ (Task 1 tables + Task 6 boot merge + Task 8 Step 5.8 manual proof), manual retirement only (no scheduler) ✅ (Task 4 `retire()` is the only removal path, no `@Scheduled` anywhere), encryption at rest ✅ (Task 2), existing `InternalTokenAuthFilter` gate reused ✅ (Task 5, no `SecurityConfig` changes needed since `/internal/**` is already `permitAll()`), 404 for YAML/unknown kid vs 409 for active kid ✅ (Task 4's `retire()` checks DB-presence before active-check, matching the design's stated precedence), commit-before-mutate ✅ (Task 4 uses `TransactionTemplate` explicitly, not a proxy-based `@Transactional`, and Task 4's test asserts `commits == 1` before checking the registry).
- **Placeholder scan:** none — every step has runnable code.
- **Type consistency:** `JwtKeyRotationService.RotatedKey`, `KeyEncryptionUtil`, `JwtSigningKeyJpaRepository`/`JwtActiveSigningKeyJpaRepository`, and the new exceptions are defined once (Tasks 1, 2, 4) and referenced with identical names/signatures in Tasks 4–7; `JwtKeyRegistry`'s `addKey`/`setActiveKid`/`removeKey` (Task 3) match exactly how Task 4 and Task 7's test call them.
