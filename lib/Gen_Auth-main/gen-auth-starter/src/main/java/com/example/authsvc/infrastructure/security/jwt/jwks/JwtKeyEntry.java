package com.example.authsvc.infrastructure.security.jwt.jwks;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Immutable container for one RSA key pair / KMS-backed key associated with a specific {@code kid}.
 *
 * <p>In <b>local</b> signing mode both {@code privateKey} and {@code publicKey} are populated
 * from PEM files; {@code kmsKeyId} is {@code null}.
 *
 * <p>In <b>KMS</b> signing mode {@code privateKey} is always {@code null} — the private key
 * never leaves AWS KMS.  {@code publicKey} is fetched from KMS at startup (or loaded from an
 * optional PEM file) so JWKS can expose it.  {@code kmsKeyId} holds the ARN / alias of the
 * KMS key used for signing.
 */
public record JwtKeyEntry(
        String        kid,
        RSAPrivateKey privateKey,   // null in KMS mode — private key never leaves KMS
        RSAPublicKey  publicKey,
        String        kmsKeyId      // null in local mode
) {}
