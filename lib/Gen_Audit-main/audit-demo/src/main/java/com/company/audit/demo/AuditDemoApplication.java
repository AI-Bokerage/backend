package com.company.audit.demo;

import com.company.audit.spring.config.AuditProperties;
import java.net.URI;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * A minimal, unpublished demo application for manually exercising the audit starter against
 * real Postgres, Mongo, RabbitMQ, and LocalStack (S3) instances. This module carries no
 * automated tests — those live in {@code audit-spring-boot-starter}.
 */
@SpringBootApplication
public class AuditDemoApplication {

    /**
     * Boots the demo application.
     *
     * @param args command-line arguments, forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(AuditDemoApplication.class, args);
    }

    /**
     * Declares the queue {@code RabbitEventConsumer} listens on so RabbitAdmin creates it
     * automatically at startup. Queue provisioning is an application-level concern, not
     * something {@code audit-spring-boot-starter} does on a consumer's behalf.
     *
     * @param queueName the configured queue name
     * @return the queue to declare
     */
    @Bean
    public Queue auditEventsQueue(@Value("${audit.rabbit.queue:audit.events}") String queueName) {
        return new Queue(queueName, true);
    }

    /**
     * Overrides the starter's default {@link S3Client} bean (which uses the AWS SDK's normal
     * region/credentials resolution, appropriate for real S3) to point at this demo's LocalStack
     * instance instead, with LocalStack's fixed dummy credentials.
     *
     * @return an S3 client pointed at LocalStack
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true)
                .build();
    }

    /**
     * Creates the anchor bucket, with Object Lock enabled, on startup if it doesn't already
     * exist. Bucket provisioning (including Object Lock enablement, which can only be set at
     * bucket-creation time) is an application/infrastructure concern, not something
     * {@code audit-spring-boot-starter} does on a consumer's behalf — a real deployment would do
     * this via infrastructure-as-code, not application code; this exists purely so the manual
     * demo loop is self-contained.
     *
     * @param s3Client the S3 client to create the bucket with
     * @param properties the starter's configuration properties
     * @return a runner that ensures the bucket exists
     */
    @Bean
    public CommandLineRunner createAnchorBucketIfAbsent(S3Client s3Client, AuditProperties properties) {
        return args -> {
            String bucket = properties.getAnchor().getBucket();
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (S3Exception e) {
                s3Client.createBucket(
                        CreateBucketRequest.builder().bucket(bucket).objectLockEnabledForBucket(true).build());
            }
        };
    }
}
