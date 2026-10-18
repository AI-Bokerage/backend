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
        // ponytail: rotate()/retire() aren't mutually serialized against each other or
        // themselves — two concurrent rotate() calls, or a rotate() racing a retire() on
        // the same kid, can leave the DB active-kid row and the in-memory registry's
        // active kid transiently divergent (self-heals on the next restart, since boot
        // always reloads from the DB). Add a distributed lock or single-writer constraint
        // if concurrent operator calls become a real operational issue.
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
        // ponytail: same ceiling as rotate() above — the active-kid check here and the
        // DB delete + registry.removeKey() below aren't atomic with a concurrent rotate()
        // on this kid. Worst case the DB row is deleted but registry.removeKey() then
        // throws because a race promoted this kid active in between (500 to the caller;
        // DB/registry mismatch self-heals on restart).
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
