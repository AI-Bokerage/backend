package com.example.authsvc.infrastructure.security.jwt.kms;

import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.signer.JwtSigner;
import com.example.authsvc.common.exception.JwtSigningException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.KmsException;

/**
 * {@link JwtSigner} implementation that delegates RS256 signing to AWS KMS.
 *
 * <p>This implementation is selected when {@code jwt.signing-mode=kms}.  The private key
 * never leaves the KMS HSM; the KMS key must be an <em>asymmetric RSA 2048-bit</em> or
 * larger key with the {@code SIGN_VERIFY} key usage and {@code RSASSA_PKCS1_V1_5_SHA_256}
 * signing algorithm enabled.
 *
 * <p>The signature returned by {@code RSASSA_PKCS1_V1_5_SHA_256} is raw PKCS#1 v1.5 RSA
 * signature bytes — identical to what Java's {@code Signature.sign()} produces for
 * {@code SHA256withRSA} — and can therefore be used directly as the RS256 signature in
 * a JWT without further transformation.
 *
 * <p>Instances are created by {@link com.example.authsvc.infrastructure.security.config.JwtSignerConfig}.
 */
@Slf4j
public class AwsKmsJwtSigner implements JwtSigner {

    private final JwtKeyRegistry registry;
    private final KmsClient      kmsClient;

    public AwsKmsJwtSigner(JwtKeyRegistry registry, KmsClient kmsClient) {
        this.registry  = registry;
        this.kmsClient = kmsClient;
    }

    @Override
    public byte[] sign(String kid, byte[] signingInput) {
        String kmsKeyId = registry.getKmsKeyId(kid);
        if (kmsKeyId == null || kmsKeyId.isBlank()) {
            throw new JwtSigningException(
                    "No KMS key ID in registry for kid '%s' — set aws.kms.key-id or jwt.keys[].kms-key-id"
                            .formatted(kid));
        }
        long start = System.currentTimeMillis();
        try {
            SignRequest request = SignRequest.builder()
                    .keyId(kmsKeyId)
                    .message(SdkBytes.fromByteArray(signingInput))
                    .messageType(MessageType.RAW)
                    .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)
                    .build();
            SignResponse response = kmsClient.sign(request);
            byte[] signature = response.signature().asByteArray();
            log.info("kms.sign.success kid={} kmsKeyId={} ms={}",
                    kid, kmsKeyId, System.currentTimeMillis() - start);
            return signature;
        } catch (KmsException e) {
            log.error("kms.sign.failure kid={} kmsKeyId={} error={}", kid, kmsKeyId, e.getMessage());
            throw new JwtSigningException("AWS KMS signing failed for kid: " + kid, e);
        }
    }
}
