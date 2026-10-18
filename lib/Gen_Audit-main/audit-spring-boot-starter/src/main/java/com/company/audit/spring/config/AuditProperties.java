package com.company.audit.spring.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the audit starter, bound under {@code audit}.
 *
 * <p>Annotated {@code @Validated}, but that annotation alone guarantees nothing: it only takes
 * effect if a JSR-380 validator implementation (Hibernate Validator, pulled in transitively by
 * {@code spring-boot-starter-validation}) is actually present on the <em>consuming</em>
 * application's classpath. Without one, Spring silently skips validation entirely — this starter
 * does not, and cannot, force a validator dependency onto every consumer, so a misconfigured
 * property (see {@link Anchor#getBucket()}) surfaces here at startup only for applications that
 * already depend on {@code spring-boot-starter-validation} themselves; for every other
 * application, that same misconfiguration still surfaces the same way it always did — as a later,
 * less obvious failure inside whichever adapter actually uses the property.
 */
@ConfigurationProperties(prefix = "audit")
@Validated
public class AuditProperties {

    /**
     * Creates a new properties instance with every nested group defaulted.
     */
    public AuditProperties() {
    }

    @NestedConfigurationProperty
    private final Jpa jpa = new Jpa();

    @NestedConfigurationProperty
    private final Mongo mongo = new Mongo();

    @Valid
    @NestedConfigurationProperty
    private final Rabbit rabbit = new Rabbit();

    @NestedConfigurationProperty
    private final Verification verification = new Verification();

    @Valid
    @NestedConfigurationProperty
    private final Anchor anchor = new Anchor();

    /**
     * Returns the JPA-related properties, bound under {@code audit.jpa}.
     *
     * @return the JPA properties
     */
    public Jpa getJpa() {
        return jpa;
    }

    /**
     * Returns the Mongo-related properties, bound under {@code audit.mongo}.
     *
     * @return the Mongo properties
     */
    public Mongo getMongo() {
        return mongo;
    }

    /**
     * Returns the Rabbit-related properties, bound under {@code audit.rabbit}.
     *
     * @return the Rabbit properties
     */
    public Rabbit getRabbit() {
        return rabbit;
    }

    /**
     * Returns the verification-related properties, bound under {@code audit.verification}.
     *
     * @return the verification properties
     */
    public Verification getVerification() {
        return verification;
    }

    /**
     * Returns the anchoring-related properties, bound under {@code audit.anchor}.
     *
     * @return the anchor properties
     */
    public Anchor getAnchor() {
        return anchor;
    }

    /**
     * Properties for the JPA/Postgres ledger adapter, bound under {@code audit.jpa}.
     */
    public static class Jpa {

        /**
         * Creates a new instance with {@link #enabled} defaulted to {@code true}.
         */
        public Jpa() {
        }

        private boolean enabled = true;
        private Duration partitionLockTimeout = Duration.ofSeconds(5);
        private int appendMaxAttempts = 20;

        /**
         * Returns whether the JPA auto-configuration is enabled.
         *
         * @return {@code true} if enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether the JPA auto-configuration is enabled.
         *
         * @param enabled {@code true} to enable it
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns how long {@code JpaChainRepository.append} waits to acquire a partition's
         * Postgres advisory lock before giving up.
         *
         * <p>Bounds only the time spent <em>waiting to acquire</em> the lock (via
         * {@code SET LOCAL lock_timeout}) — a transaction that hangs for an unrelated reason
         * after already acquiring the lock is a different failure mode, also bounded by the same
         * duration via {@code SET LOCAL statement_timeout}.
         *
         * @return the partition lock timeout, defaulting to 5 seconds
         */
        public Duration getPartitionLockTimeout() {
            return partitionLockTimeout;
        }

        /**
         * Sets how long {@code JpaChainRepository.append} waits to acquire a partition's
         * Postgres advisory lock before giving up.
         *
         * @param partitionLockTimeout the partition lock timeout
         */
        public void setPartitionLockTimeout(Duration partitionLockTimeout) {
            this.partitionLockTimeout = partitionLockTimeout;
        }

        /**
         * Returns the maximum number of append attempts {@code AuditAppender} retries a
         * partition-lock-related {@code SeqConflictException} before giving up.
         *
         * <p>Defaults to 20, not {@code audit-core}'s own bare default of 3: measured evidence
         * (re-running Phase 2's 20-thread single-JVM concurrency test against the Phase 8
         * advisory-lock mechanism) showed 3 attempts is provably insufficient under genuine
         * N-way contention on one partition — each retry round only ever drains exactly one
         * winner when every contender reads the same stale tip, so the unluckiest of N
         * contenders needs up to N attempts, not a small constant.
         *
         * <p>Re-evaluated, not merely assumed, after {@code DefaultAuditAppender} gained
         * jittered backoff between retries: lower values (8, then 12) were actually tried against
         * the same 20-thread regression test. 8 was flaky (roughly 1 failure in 10 runs); 12
         * passed reliably (10/10) but measurably worsened the informational 100-way
         * {@code PartitionLoadTest} exhaustion rate (~25%, versus ~5–9% at 20) for no
         * corresponding latency or resource benefit — jitter's cost is small enough that spending
         * down the retry budget just to prove a lower number is technically survivable isn't a
         * good trade. 20 remains the default: the smallest value that reliably covers this
         * starter's own worst-case regression test <em>with</em> comfortable margin, not merely
         * the smallest value that happens to pass once. See
         * {@code docs/adr/0001-distributed-ordering-advisory-locks.md} and {@code CHANGELOG.md}'s
         * Phase 8 entry for the measured numbers this default is based on.
         *
         * @return the maximum append attempts, defaulting to 20
         */
        public int getAppendMaxAttempts() {
            return appendMaxAttempts;
        }

        /**
         * Sets the maximum number of append attempts {@code AuditAppender} retries a
         * partition-lock-related {@code SeqConflictException} before giving up.
         *
         * @param appendMaxAttempts the maximum append attempts; must be at least 1
         */
        public void setAppendMaxAttempts(int appendMaxAttempts) {
            this.appendMaxAttempts = appendMaxAttempts;
        }
    }

    /**
     * Properties for the Mongo rich-event-store adapter, bound under {@code audit.mongo}.
     */
    public static class Mongo {

        /**
         * Creates a new instance with the collection name defaulted.
         */
        public Mongo() {
        }

        private String collection = "audit_events";

        /**
         * Returns the Mongo collection name events are stored in.
         *
         * @return the collection name
         */
        public String getCollection() {
            return collection;
        }

        /**
         * Sets the Mongo collection name events are stored in.
         *
         * @param collection the collection name
         */
        public void setCollection(String collection) {
            this.collection = collection;
        }
    }

    /**
     * Properties for the Rabbit consumer, bound under {@code audit.rabbit}.
     */
    public static class Rabbit {

        /**
         * Creates a new instance with the queue name defaulted.
         */
        public Rabbit() {
        }

        private String queue = "audit.events";

        @Valid
        @NestedConfigurationProperty
        private final Shard shard = new Shard();

        /**
         * Returns the queue {@code RabbitEventConsumer} listens on.
         *
         * @return the queue name
         */
        public String getQueue() {
            return queue;
        }

        /**
         * Sets the queue {@code RabbitEventConsumer} listens on.
         *
         * @param queue the queue name
         */
        public void setQueue(String queue) {
            this.queue = queue;
        }

        /**
         * Returns the sharding-related properties, bound under {@code audit.rabbit.shard}.
         *
         * @return the shard properties
         */
        public Shard getShard() {
            return shard;
        }

        /**
         * Properties for partition-ownership sharding, bound under {@code audit.rabbit.shard}.
         *
         * <p>Defaults ({@code instanceIndex=0}, {@code totalInstances=1}) are a deliberate no-op:
         * with exactly one configured instance, every partition hashes to instance 0 — the only
         * instance that exists — so every single-instance deployment (including
         * {@code audit-demo}) keeps working with zero configuration changes.
         */
        public static class Shard {

            /**
             * Creates a new instance with both properties defaulted to the single-instance,
             * no-op configuration.
             */
            public Shard() {
            }

            @Min(0)
            private int instanceIndex = 0;

            @Min(1)
            private int totalInstances = 1;

            /**
             * Returns this instance's own index within the configured topology.
             *
             * <p>{@code @Min(0)} rejects a negative index at startup; it cannot, by itself, catch
             * an index that is merely out of range for a given {@link #getTotalInstances()} (e.g.
             * {@code instanceIndex=3} with {@code totalInstances=2}) — that would require a
             * cross-field constraint this class does not yet declare, so it still depends on the
             * deploying platform configuring both values consistently.
             *
             * @return this instance's index, defaulting to 0
             */
            public int getInstanceIndex() {
                return instanceIndex;
            }

            /**
             * Sets this instance's own index within the configured topology.
             *
             * @param instanceIndex this instance's index; must be in {@code [0, totalInstances)},
             *     though only the {@code >= 0} half of that range is enforced via {@code @Min(0)}
             */
            public void setInstanceIndex(int instanceIndex) {
                this.instanceIndex = instanceIndex;
            }

            /**
             * Returns the total number of instances sharing partition ownership.
             *
             * <p>Configuration consistency across instances — every real instance agreeing on
             * this same total, each with a distinct {@link #getInstanceIndex()} — is the
             * deploying platform's responsibility; this library has no way to validate it from
             * inside any single instance. This library <em>can</em>, and does, validate that this
             * single instance's own value is at least 1: {@code 0} would make
             * {@code ConsistentHashPartitionShardResolver}'s {@code Math.floorMod(x, 0)} throw an
             * {@code ArithmeticException} on the very first inbound message rather than failing
             * clearly at startup — found via a release-readiness review, not assumed already
             * handled, and closed with a {@code @Min(1)} constraint rather than left as a
             * confusing runtime crash.
             *
             * @return the total instance count, defaulting to 1
             */
            public int getTotalInstances() {
                return totalInstances;
            }

            /**
             * Sets the total number of instances sharing partition ownership.
             *
             * @param totalInstances the total instance count; must be at least 1 — enforced by
             *     {@code @Min(1)}, but (like every other constraint on this class) only if a
             *     JSR-380 validator (e.g. {@code spring-boot-starter-validation}) is present on
             *     the <em>consuming</em> application's classpath and this field's {@code @Valid}
             *     cascade from {@link Rabbit#getShard()} up through {@link AuditProperties} is
             *     intact
             */
            public void setTotalInstances(int totalInstances) {
                this.totalInstances = totalInstances;
            }
        }
    }

    /**
     * Properties for scheduled chain verification, bound under {@code audit.verification}.
     */
    public static class Verification {

        /**
         * Creates a new instance with the cron expression defaulted.
         */
        public Verification() {
        }

        private String cron = "0 0 2 * * *";

        /**
         * Returns the cron expression {@code ChainVerifierJob} runs on. Defaults to daily at
         * 02:00, matching the source spec.
         *
         * @return the cron expression
         */
        public String getCron() {
            return cron;
        }

        /**
         * Sets the cron expression {@code ChainVerifierJob} runs on.
         *
         * @param cron the cron expression
         */
        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    /**
     * Properties for anchor publishing, bound under {@code audit.anchor}.
     */
    public static class Anchor {

        /**
         * Creates a new instance with every property except {@code bucket} defaulted.
         */
        public Anchor() {
        }

        @NotBlankIfPresent
        private String bucket;
        private String keyPrefix = "anchors/";
        private int retentionYears = 7;
        private String publishCron = "0 0 3 * * *";

        /**
         * Returns the S3 bucket anchors are published to.
         *
         * <p>No default: a wrong default silently writing to the wrong bucket is worse than
         * failing to start, so this must be explicitly configured. {@code null} (never
         * configured at all — the common case for applications that don't use anchoring) is
         * valid; a blank/whitespace-only value is not, and fails fast at startup instead of
         * surfacing later as an unrelated AWS region-resolution error — see
         * {@link NotBlankIfPresent}.
         *
         * @return the bucket name
         */
        public String getBucket() {
            return bucket;
        }

        /**
         * Sets the S3 bucket anchors are published to.
         *
         * @param bucket the bucket name
         */
        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        /**
         * Returns the key prefix every anchor object is written under.
         *
         * @return the key prefix
         */
        public String getKeyPrefix() {
            return keyPrefix;
        }

        /**
         * Sets the key prefix every anchor object is written under.
         *
         * @param keyPrefix the key prefix
         */
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        /**
         * Returns how many years of Compliance-mode retention to apply to each anchor.
         *
         * @return the retention period, in years
         */
        public int getRetentionYears() {
            return retentionYears;
        }

        /**
         * Sets how many years of Compliance-mode retention to apply to each anchor.
         *
         * @param retentionYears the retention period, in years
         */
        public void setRetentionYears(int retentionYears) {
            this.retentionYears = retentionYears;
        }

        /**
         * Returns the cron expression {@code AnchorPublisherJob} runs on. Defaults to daily at
         * 03:00, an hour after scheduled chain verification.
         *
         * @return the cron expression
         */
        public String getPublishCron() {
            return publishCron;
        }

        /**
         * Sets the cron expression {@code AnchorPublisherJob} runs on.
         *
         * @param publishCron the cron expression
         */
        public void setPublishCron(String publishCron) {
            this.publishCron = publishCron;
        }
    }
}
