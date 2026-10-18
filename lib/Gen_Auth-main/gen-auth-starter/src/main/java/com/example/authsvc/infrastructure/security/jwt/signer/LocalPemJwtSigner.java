package com.example.authsvc.infrastructure.security.jwt.signer;

import com.example.authsvc.common.exception.JwtSigningException;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import lombok.extern.slf4j.Slf4j;

import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;

/**
 * {@link JwtSigner} implementation that signs JWT payloads using an RSA private key
 * loaded from a PEM file by {@link JwtKeyRegistry}.
 *
 * <p>This implementation is selected when {@code jwt.signing-mode=local} (the default).
 * It is safe for local development and CI, and viable for production deployments where
 * AWS KMS is not required.
 *
 * <p>Instances are created by {@link com.example.authsvc.infrastructure.security.config.JwtSignerConfig}.
 */
@Slf4j
public class LocalPemJwtSigner implements JwtSigner {

    private final JwtKeyRegistry registry;

    public LocalPemJwtSigner(JwtKeyRegistry registry) {
        this.registry = registry;
    }

    @Override
    public byte[] sign(String kid, byte[] signingInput) {
        RSAPrivateKey privateKey = registry.getPrivateKey(kid);
        if (privateKey == null) {
            throw new JwtSigningException(
                    "No private key in registry for kid '%s' — ensure jwt.signing-mode=local and privateKeyPath is set"
                            .formatted(kid));
        }
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput);
            byte[] signature = sig.sign();
            log.debug("jwt.sign.local kid={}", kid);
            return signature;
        } catch (GeneralSecurityException e) {
            log.error("jwt.sign.local.failure kid={} error={}", kid, e.getMessage());
            throw new JwtSigningException("Local JWT signing failed for kid: " + kid, e);
        }
    }
}
