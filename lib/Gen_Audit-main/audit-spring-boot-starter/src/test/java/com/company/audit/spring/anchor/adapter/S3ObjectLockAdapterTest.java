package com.company.audit.spring.anchor.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.HashValue;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRetentionRequest;
import software.amazon.awssdk.services.s3.model.ObjectLockRetention;
import software.amazon.awssdk.services.s3.model.ObjectLockRetentionMode;

/**
 * Publishes a real anchor against a Testcontainers LocalStack S3 endpoint.
 *
 * <p><b>What this actually proves, and what it doesn't:</b> LocalStack's S3 Object Lock support
 * is not a faithful WORM guarantee the way real S3 Object Lock (Compliance mode) is — LocalStack
 * accepts and stores the retention configuration but does not enforce immutability against it the
 * way AWS's real S3 does. This test proves that {@link S3ObjectLockAdapter} makes the correct API
 * calls with the correct retention configuration (mode, retain-until date, key structure, body
 * content) — not that the object is actually un-deletable. That stronger guarantee can only be
 * proven against real AWS S3, which this project's test suite deliberately does not attempt.
 */
class S3ObjectLockAdapterTest {

    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    private static S3Client s3Client;

    static {
        LOCALSTACK.start();
    }

    @BeforeAll
    static void createClient() {
        s3Client = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    @AfterAll
    static void closeClient() {
        s3Client.close();
    }

    @Test
    void publishAnchorWritesObjectWithExpectedKeyBodyAndRetentionConfiguration() throws Exception {
        String bucket = "s3-object-lock-adapter-test-" + System.currentTimeMillis();
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).objectLockEnabledForBucket(true).build());

        S3ObjectLockAdapter adapter = new S3ObjectLockAdapter(s3Client, bucket, "anchors/", 7);

        String partitionKey = "s3-adapter-test-partition";
        HashValue tipHash = HashValue.of(MessageDigest.getInstance("SHA-256").digest("adapter-test".getBytes()));
        Instant publishedAt = Instant.parse("2024-06-01T12:00:00Z");

        String storageReference = adapter.publishAnchor(partitionKey, tipHash, publishedAt);

        assertThat(storageReference).contains("etag=").contains("versionId=");

        String expectedKey = "anchors/" + partitionKey + "/" + publishedAt + ".json";
        String bodyText = new String(
                s3Client
                        .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(expectedKey).build())
                        .asByteArray(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(bodyText).contains("\"partitionKey\":\"" + partitionKey + "\"");
        assertThat(bodyText).contains("\"tipHash\":\"" + tipHash.hex() + "\"");
        assertThat(bodyText).contains("\"publishedAt\":\"" + publishedAt + "\"");

        ObjectLockRetention retention = s3Client
                .getObjectRetention(GetObjectRetentionRequest.builder().bucket(bucket).key(expectedKey).build())
                .retention();
        assertThat(retention.mode()).isEqualTo(ObjectLockRetentionMode.COMPLIANCE);
        assertThat(retention.retainUntilDate()).isAfter(publishedAt.plusSeconds(6L * 365 * 24 * 3600));
    }
}
