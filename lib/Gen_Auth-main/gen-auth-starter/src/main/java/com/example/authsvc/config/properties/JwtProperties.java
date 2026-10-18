package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String          issuer;

    /**
     * Token audience (the {@code aud} claim). Consumers (e.g. usg-svc's JWKS
     * verifier) reject tokens whose audience does not match their expected value,
     * so this must agree with the downstream services' {@code JWT_AUDIENCE}.
     */
    private String          audience;

    /** Legacy single-key id — still used as the default {@link #resolvedActiveKid()} fallback. */
    private String          keyId        = "auth-key-v1";

    /**
     * Signing backend.
     * <ul>
     *   <li>{@code local} — signs using the RSA private key loaded from the PEM file
     *       (default; zero-config for local / CI environments)</li>
     *   <li>{@code kms} — delegates signing to AWS KMS; private keys never leave KMS</li>
     * </ul>
     */
    private String signingMode = "local";   // "local" | "kms"

    /**
     * Active signing kid for the multi-key registry.
     * When blank, falls back to {@link #keyId} so that existing deployments that
     * do not yet configure {@code jwt.keys} continue to work unchanged.
     */
    private String          activeKid;

    private AccessToken     accessToken  = new AccessToken();
    private RefreshToken    refreshToken = new RefreshToken();

    /** Legacy single-key PEM paths — still used by the {@code rsaPrivateKey}/{@code rsaPublicKey} beans. */
    private Rsa             rsa          = new Rsa();

    /**
     * Multi-key registry definition.
     * When empty the registry falls back to the legacy {@link #rsa} + {@link #keyId} configuration,
     * so no YAML change is required for existing single-key deployments.
     */
    private List<KeyEntry>  keys         = new ArrayList<>();

    // ─── Nested types ─────────────────────────────────────────────────────────

    @Data
    public static class AccessToken {
        private int expirationMinutes = 15;
    }

    @Data
    public static class RefreshToken {
        private int expirationDays = 7;
    }

    @Data
    public static class Rsa {
        private String privateKeyPath = "classpath:keys/private.pem";
        private String publicKeyPath  = "classpath:keys/public.pem";
    }

    /** One entry in the {@code jwt.keys} list. */
    @Data
    public static class KeyEntry {
        /** Unique key identifier embedded in JWT headers and JWKS. */
        private String kid;
        /**
         * Resource path to the PKCS#8 PEM private key.
         * Required in {@code local} signing mode; ignored in {@code kms} mode.
         */
        private String privateKeyPath;
        /**
         * Resource path to the X.509 PEM public key.
         * Optional in {@code kms} mode — if blank the public key is fetched from KMS
         * via {@code GetPublicKey} at startup.  Always required in {@code local} mode.
         */
        private String publicKeyPath;
        /**
         * KMS key ID (ARN or alias) for this specific kid.
         * Used only in {@code kms} signing mode. When blank, falls back to the
         * global {@code aws.kms.key-id}.
         */
        private String kmsKeyId;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the kid that the registry should designate as the active signer.
     * Uses {@link #activeKid} when set; falls back to {@link #keyId} for backward
     * compatibility with deployments that have not yet adopted the new config.
     */
    public String resolvedActiveKid() {
        return (activeKid != null && !activeKid.isBlank()) ? activeKid : keyId;
    }
}
