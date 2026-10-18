package com.company.audit.spring.anchor.adapter;

import com.company.audit.core.api.HashValue;
import com.company.audit.core.port.ObjectLockPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * An {@link ObjectLockPort} backed by S3 Object Lock.
 *
 * <p>Not component-scanned: registered explicitly as a bean by
 * {@code AuditAnchorAutoConfiguration}, matching how a starter's internal wiring is declared
 * elsewhere in this module.
 *
 * <p>Anchor objects are append-only by construction: the key includes both {@code partitionKey}
 * and an ISO-8601 timestamp, so every publish produces a new object rather than overwriting the
 * prior anchor for that partition.
 *
 * <p>The published body is a small JSON document (partition key, hex event hash, published-at
 * instant), not raw bytes, so a human or tool reading the bucket directly can understand what's
 * there without this library's code.
 *
 * <p>The returned storage reference packs the put response's ETag and VersionId — captured
 * because the S3 API call already returns them as part of the response being made anyway, not
 * because of any additional call.
 */
public class S3ObjectLockAdapter implements ObjectLockPort {

    private final S3Client s3Client;
    private final String bucket;
    private final String keyPrefix;
    private final int retentionYears;

    /**
     * Creates a new adapter.
     *
     * @param s3Client the S3 client to publish anchors through
     * @param bucket the bucket anchors are published to
     * @param keyPrefix the key prefix every anchor object is written under
     * @param retentionYears how many years of Compliance-mode retention to apply to each anchor
     */
    public S3ObjectLockAdapter(S3Client s3Client, String bucket, String keyPrefix, int retentionYears) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
        this.retentionYears = retentionYears;
    }

    @Override
    public String publishAnchor(String partitionKey, HashValue tipHash, Instant publishedAt) {
        String key = keyPrefix + partitionKey + "/" + publishedAt.toString() + ".json";
        String body = toJson(partitionKey, tipHash, publishedAt);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .objectLockMode(ObjectLockMode.COMPLIANCE)
                .objectLockRetainUntilDate(publishedAt.plus(365L * retentionYears, ChronoUnit.DAYS))
                .build();

        PutObjectResponse response = s3Client.putObject(request, RequestBody.fromString(body, StandardCharsets.UTF_8));

        return "etag=" + response.eTag() + ";versionId=" + response.versionId();
    }

    private String toJson(String partitionKey, HashValue tipHash, Instant publishedAt) {
        return "{\"partitionKey\":\"" + escape(partitionKey) + "\","
                + "\"tipHash\":\"" + tipHash.hex() + "\","
                + "\"publishedAt\":\"" + publishedAt + "\"}";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
