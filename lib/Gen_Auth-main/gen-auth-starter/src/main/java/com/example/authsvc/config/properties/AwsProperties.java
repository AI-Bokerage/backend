package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS client configuration, bound from the {@code aws.*} namespace.
 *
 * <p>Credentials (access key / secret key) are optional: when blank the AWS SDK v2
 * default credential provider chain is used automatically (environment variables
 * {@code AWS_ACCESS_KEY_ID} / {@code AWS_SECRET_ACCESS_KEY}, EC2 instance profile,
 * ECS task role, etc.).  Explicit property-based credentials are supported for
 * environments where environment variables are not convenient.
 *
 * <p>The region defaults to {@code ap-south-1} but should always be overridden
 * via the {@code AWS_REGION} environment variable or the {@code aws.region} property.
 */
@Data
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    /** AWS region for all SDK clients (e.g. {@code ap-south-1}). */
    private String region = "ap-south-1";

    /**
     * Optional explicit AWS access key ID.
     * When blank the SDK resolves credentials via its default chain.
     */
    private String accessKeyId;

    /**
     * Optional explicit AWS secret access key.
     * When blank the SDK resolves credentials via its default chain.
     */
    private String secretAccessKey;

    /** KMS-specific settings. */
    private Kms kms = new Kms();

    @Data
    public static class Kms {
        /**
         * Global fallback KMS key ID (ARN or alias).
         * Can be overridden per-kid by setting {@code kms-key-id} on a
         * {@link JwtProperties.KeyEntry}.
         */
        private String keyId;
    }
}
