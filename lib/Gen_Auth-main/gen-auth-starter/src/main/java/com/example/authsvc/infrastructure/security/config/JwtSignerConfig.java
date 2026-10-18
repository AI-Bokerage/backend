package com.example.authsvc.infrastructure.security.config;

import com.example.authsvc.config.properties.AwsProperties;
import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.kms.AwsKmsJwtSigner;
import com.example.authsvc.infrastructure.security.jwt.signer.JwtSigner;
import com.example.authsvc.infrastructure.security.jwt.signer.LocalPemJwtSigner;
import com.example.authsvc.infrastructure.security.jwt.signer.JwtSigner;
import com.example.authsvc.infrastructure.security.jwt.signer.LocalPemJwtSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.KmsClientBuilder;

/**
 * Creates the {@link JwtSigner} bean and, when KMS mode is active, the {@link KmsClient} bean.
 *
 * <p>The active implementation is controlled by {@code jwt.signing-mode}:
 * <ul>
 *   <li>{@code local} (default) — {@link LocalPemJwtSigner}: signs using RSA private key from PEM</li>
 *   <li>{@code kms} — {@link AwsKmsJwtSigner}: delegates signing to AWS KMS</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JwtSignerConfig {

    private final JwtProperties jwtProperties;
    private final AwsProperties awsProperties;

    /**
     * AWS KMS client — only instantiated when {@code jwt.signing-mode=kms}.
     *
     * <p>Credentials are resolved in this order:
     * <ol>
     *   <li>Explicit {@code aws.access-key-id} / {@code aws.secret-access-key} properties
     *       (not recommended for production; prefer IAM roles)</li>
     *   <li>AWS default credential provider chain (environment variables,
     *       EC2 instance profile, ECS task role, etc.)</li>
     * </ol>
     */
    @Bean
    @ConditionalOnProperty(name = "jwt.signing-mode", havingValue = "kms")
    public KmsClient kmsClient() {
        String region = awsProperties.getRegion();
        KmsClientBuilder builder = KmsClient.builder().region(Region.of(region));

        String accessKey = awsProperties.getAccessKeyId();
        String secretKey = awsProperties.getSecretAccessKey();
        if (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
            log.info("kms.client.initialized region={} credentials=static", region);
        } else {
            log.info("kms.client.initialized region={} credentials=default-chain", region);
        }
        return builder.build();
    }

    /**
     * KMS-backed JWT signer — only activated when {@code jwt.signing-mode=kms}.
     * Depends on the {@link KmsClient} bean created above.
     */
    @Bean
    @ConditionalOnProperty(name = "jwt.signing-mode", havingValue = "kms")
    public JwtSigner kmsJwtSigner(JwtKeyRegistry registry, KmsClient kmsClient) {
        log.info("jwt.signer.initialized mode=kms");
        return new AwsKmsJwtSigner(registry, kmsClient);
    }

    /**
     * Local PEM-backed JWT signer — active when {@code jwt.signing-mode=local}
     * or when the property is absent (default).
     */
    @Bean
    @ConditionalOnProperty(name = "jwt.signing-mode", havingValue = "local", matchIfMissing = true)
    public JwtSigner localJwtSigner(JwtKeyRegistry registry) {
        log.info("jwt.signer.initialized mode=local");
        return new LocalPemJwtSigner(registry);
    }
}
