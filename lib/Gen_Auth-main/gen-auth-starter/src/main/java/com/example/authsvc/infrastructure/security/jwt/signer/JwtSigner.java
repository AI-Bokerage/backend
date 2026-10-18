package com.example.authsvc.infrastructure.security.jwt.signer;

import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.kms.AwsKmsJwtSigner;

/**
 * Strategy interface for RS256 JWT signing.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link LocalPemJwtSigner} — signs using an RSA private key loaded from a PEM
 *       file at startup (default; zero-infrastructure requirement)</li>
 *   <li>{@link AwsKmsJwtSigner} — delegates to AWS KMS so the private key never leaves
 *       the HSM (production / compliance mode)</li>
 * </ul>
 *
 * <p>The active implementation is selected at startup by
 * {@code config.security.JwtSignerConfig} based on the {@code jwt.signing-mode} property.
 */
public interface JwtSigner {

    /**
     * Computes an RS256 signature over the JWT signing input and returns the raw
     * PKCS#1 v1.5 signature bytes.
     *
     * <p>The {@code signingInput} is the ASCII byte representation of
     * {@code base64url(header) + "." + base64url(payload)} — i.e. exactly the string
     * that must be covered by the RS256 signature per RFC 7515 §5.2.
     *
     * @param kid          the key identifier — must match an entry in {@link JwtKeyRegistry}
     * @param signingInput ASCII bytes of the header.payload signing input
     * @return raw RSA-PKCS1v15-SHA256 signature bytes (not base64-encoded)
     * @throws com.example.authsvc.common.exception.JwtSigningException on any signing failure
     */
    byte[] sign(String kid, byte[] signingInput);
}
