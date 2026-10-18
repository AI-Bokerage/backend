package com.example.authsvc.infrastructure.security.config;

import com.example.authsvc.config.properties.AwsProperties;
import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyEntry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads RSA key material and constructs the {@link JwtKeyRegistry}.
 *
 * <h3>Single-key (legacy) mode</h3>
 * When {@code jwt.keys} is empty the registry is populated automatically from
 * {@code jwt.rsa.private-key-path} / {@code jwt.rsa.public-key-path} under
 * {@code kid = jwt.key-id}.  No YAML change is required for existing deployments.
 *
 * <h3>Multi-key local mode ({@code jwt.signing-mode=local})</h3>
 * Populate {@code jwt.keys} with one or more entries (each with both PEM paths)
 * and set {@code jwt.active-kid} to designate the signing key.
 *
 * <h3>Multi-key KMS mode ({@code jwt.signing-mode=kms})</h3>
 * Each key entry only requires {@code public-key-path} (or it is auto-fetched from
 * KMS via {@code GetPublicKey}).  The private key is managed entirely inside KMS.
 * Set either per-key {@code kms-key-id} or the global {@code aws.kms.key-id}.
 *
 * <h3>Retained legacy beans</h3>
 * {@code rsaPublicKey()} is always produced; {@code rsaPrivateKey()} is produced only
 * in local signing mode.  Both are retained for backward compatibility with
 * Spring Security's auto-configured {@code NimbusJwtDecoder}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RsaKeyConfig {

    private final JwtProperties jwtProperties;
    private final AwsProperties awsProperties;
    private final com.example.authsvc.infrastructure.persistence.repository.JwtSigningKeyJpaRepository signingKeyRepository;
    private final com.example.authsvc.infrastructure.persistence.repository.JwtActiveSigningKeyJpaRepository activeKeyRepository;
    private final com.example.authsvc.infrastructure.security.jwt.jwks.KeyEncryptionUtil encryptionUtil;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    // ─── Legacy single-key beans (retained for Spring Security auto-config) ──

    /**
     * RSA private key bean — only created in {@code local} signing mode.
     * In {@code kms} mode the private key never leaves AWS KMS.
     */
    @Bean
    @ConditionalOnProperty(name = "jwt.signing-mode", havingValue = "local", matchIfMissing = true)
    public RSAPrivateKey rsaPrivateKey() throws Exception {
        long start = System.currentTimeMillis();
        String path = jwtProperties.getRsa().getPrivateKeyPath();
        log.info("Loading RSA private key from: {}", path);
        RSAPrivateKey key = loadPrivateKey(path);
        log.info("perf.startup.rsa_load.ms={} keyType=private", System.currentTimeMillis() - start);
        return key;
    }

    @Bean
    public RSAPublicKey rsaPublicKey() throws Exception {
        long start = System.currentTimeMillis();
        String path = jwtProperties.getRsa().getPublicKeyPath();
        log.info("Loading RSA public key from: {}", path);
        RSAPublicKey key = loadPublicKey(path);
        log.info("perf.startup.rsa_load.ms={} keyType=public", System.currentTimeMillis() - start);
        return key;
    }

    // ─── Multi-key registry ───────────────────────────────────────────────────

    /**
     * Builds the {@link JwtKeyRegistry} used by
     * {@link com.example.authsvc.infrastructure.security.jwt.util.JwtUtils} and
     * {@link com.example.authsvc.infrastructure.security.jwt.jwks.JwksService}.
     *
     * <p>{@code kmsClient} is {@code null} in local mode (the bean does not exist);
     * Spring injects {@code null} automatically when the bean is absent and the
     * parameter is annotated with {@link Nullable}.
     *
     * <p>Startup validation (fail-fast):
     * <ol>
     *   <li>No empty registry</li>
     *   <li>No duplicate kids</li>
     *   <li>Local mode: every keypair passes a sign-verify round-trip</li>
     *   <li>KMS mode: every public key is RSA ≥ 2048 bits</li>
     *   <li>Active kid is present in the registry</li>
     * </ol>
     */
    @Bean
    public JwtKeyRegistry jwtKeyRegistry(@Nullable KmsClient kmsClient) throws Exception {
        long start    = System.currentTimeMillis();
        boolean isKms = "kms".equalsIgnoreCase(jwtProperties.getSigningMode());

        List<JwtProperties.KeyEntry>  keyDefs  = jwtProperties.getKeys();
        Map<String, JwtKeyEntry>      registry = new LinkedHashMap<>();

        if (keyDefs.isEmpty()) {
            // ── Legacy / backward-compat fallback — always local ─────────────
            String kid = jwtProperties.getKeyId();
            if (kid == null || kid.isBlank()) {
                throw new IllegalStateException(
                        "jwt.key-id must not be blank when jwt.keys is empty");
            }
            RSAPrivateKey priv = loadPrivateKey(jwtProperties.getRsa().getPrivateKeyPath());
            RSAPublicKey  pub  = loadPublicKey(jwtProperties.getRsa().getPublicKeyPath());
            validateKeypair(kid, priv, pub);
            registry.put(kid, new JwtKeyEntry(kid, priv, pub, null));
            log.info("jwks.registry.loaded kid={} mode=legacy", kid);

        } else {
            // ── Multi-key mode ────────────────────────────────────────────────
            for (JwtProperties.KeyEntry def : keyDefs) {
                String kid = def.getKid();
                if (kid == null || kid.isBlank()) {
                    throw new IllegalStateException(
                            "Every entry in jwt.keys must have a non-blank kid");
                }
                if (registry.containsKey(kid)) {
                    throw new IllegalStateException(
                            "Duplicate kid '%s' in jwt.keys — each kid must be unique".formatted(kid));
                }

                if (isKms) {
                    // ── KMS mode: private key lives in AWS KMS ────────────────
                    if (kmsClient == null) {
                        throw new IllegalStateException(
                                "jwt.signing-mode=kms but no KmsClient bean found — check aws.* properties");
                    }
                    String       kmsKeyId = resolveKmsKeyId(def);
                    RSAPublicKey pub      = resolvePublicKeyForKms(def, kmsKeyId, kmsClient);
                    validateKmsPublicKey(kid, pub);
                    registry.put(kid, new JwtKeyEntry(kid, null, pub, kmsKeyId));
                    log.info("jwks.registry.loaded kid={} mode=kms kmsKeyId={}", kid, kmsKeyId);

                } else {
                    // ── Local mode: both keys from PEM files ──────────────────
                    if (def.getPrivateKeyPath() == null || def.getPublicKeyPath() == null) {
                        throw new IllegalStateException(
                                ("Key entry '%s' must specify both private-key-path and public-key-path " +
                                 "in local signing mode").formatted(kid));
                    }
                    RSAPrivateKey priv = loadPrivateKey(def.getPrivateKeyPath());
                    RSAPublicKey  pub  = loadPublicKey(def.getPublicKeyPath());
                    validateKeypair(kid, priv, pub);
                    registry.put(kid, new JwtKeyEntry(kid, priv, pub, null));
                    log.info("jwks.registry.loaded kid={} mode=local", kid);
                }
            }
        }

        // ── Runtime-rotated keys from Postgres (local mode only, purely additive) ──
        // Gated on !isKms: JwtKeyRotationService.rotate() already refuses to run in kms
        // mode, and a kms-mode process must never load a locally-encrypted private key
        // into memory — that would violate "private key never leaves KMS" even if the
        // row is only ever written by rotate() while in local mode.
        if (!isKms) {
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
        }

        // Resolve & validate the active signing kid — in local mode, a DB-tracked active
        // kid (set by JwtKeyRotationService.rotate()) takes precedence over the
        // YAML-configured one. In kms mode there is no DB override, same as before this
        // feature existed.
        String activeKid = isKms
                ? jwtProperties.resolvedActiveKid()
                : activeKeyRepository.findSingleton()
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

    // ─── KMS helpers ──────────────────────────────────────────────────────────

    /**
     * Resolves the KMS key ID for a key entry: per-key value takes precedence,
     * then falls back to the global {@code aws.kms.key-id} property.
     */
    private String resolveKmsKeyId(JwtProperties.KeyEntry def) {
        String perKey = def.getKmsKeyId();
        if (perKey != null && !perKey.isBlank()) {
            return perKey;
        }
        String global = awsProperties.getKms().getKeyId();
        if (global != null && !global.isBlank()) {
            return global;
        }
        throw new IllegalStateException(
                "No KMS key ID for kid '%s' — set jwt.keys[].kms-key-id or aws.kms.key-id"
                        .formatted(def.getKid()));
    }

    /**
     * Returns the public key for a KMS-backed key entry: loaded from a PEM file
     * if {@code public-key-path} is set, otherwise fetched live from KMS.
     */
    private RSAPublicKey resolvePublicKeyForKms(JwtProperties.KeyEntry def,
                                                String kmsKeyId,
                                                KmsClient kmsClient) throws Exception {
        String path = def.getPublicKeyPath();
        if (path != null && !path.isBlank()) {
            log.debug("jwks.kms.pubkey.source kid={} via=pem", def.getKid());
            return loadPublicKey(path);
        }
        log.debug("jwks.kms.pubkey.source kid={} via=kms kmsKeyId={}", def.getKid(), kmsKeyId);
        return fetchPublicKeyFromKms(kmsKeyId, kmsClient);
    }

    /**
     * Calls KMS {@code GetPublicKey} and decodes the DER-encoded SubjectPublicKeyInfo
     * as an RSA public key (identical byte format to {@code X509EncodedKeySpec}).
     */
    private RSAPublicKey fetchPublicKeyFromKms(String kmsKeyId, KmsClient kmsClient) throws Exception {
        GetPublicKeyResponse resp = kmsClient.getPublicKey(
                GetPublicKeyRequest.builder().keyId(kmsKeyId).build());
        byte[] der = resp.publicKey().asByteArray();
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    private void validateKmsPublicKey(String kid, RSAPublicKey pub) {
        int bits = pub.getModulus().bitLength();
        if (bits < 2048) {
            throw new IllegalStateException(
                    "KMS public key for kid '%s' is only %d bits; RS256 requires >= 2048"
                            .formatted(kid, bits));
        }
        log.debug("jwks.kms_key.validated kid={} keyBits={}", kid, bits);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private RSAPrivateKey loadPrivateKey(String path) throws Exception {
        String pem = readPemResource(path);
        byte[] der = decodePem(pem, "PRIVATE KEY");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey loadPublicKey(String path) throws Exception {
        String pem = readPemResource(path);
        byte[] der = decodePem(pem, "PUBLIC KEY");
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    /**
     * Verifies that the private and public key halves belong to the same RSA key pair
     * by performing a sign-then-verify round-trip on the kid bytes.
     */
    private void validateKeypair(String kid, RSAPrivateKey priv, RSAPublicKey pub) {
        try {
            byte[] data = kid.getBytes(StandardCharsets.UTF_8);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(priv);
            sig.update(data);
            byte[] signature = sig.sign();

            sig.initVerify(pub);
            sig.update(data);
            if (!sig.verify(signature)) {
                throw new IllegalStateException(
                        "Public/private key mismatch for kid '%s'".formatted(kid));
            }
            log.debug("jwks.keypair.validated kid={}", kid);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Keypair validation failed for kid '%s': %s".formatted(kid, e.getMessage()), e);
        }
    }

    private String readPemResource(String location) throws Exception {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private byte[] decodePem(String pem, String keyType) {
        String cleaned = pem
                .replace("-----BEGIN " + keyType + "-----", "")
                .replace("-----END "   + keyType + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(cleaned);
    }
}

