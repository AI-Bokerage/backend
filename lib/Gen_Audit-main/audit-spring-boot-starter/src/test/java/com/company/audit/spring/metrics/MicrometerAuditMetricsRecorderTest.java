package com.company.audit.spring.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerAuditMetricsRecorderTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MicrometerAuditMetricsRecorder recorder = new MicrometerAuditMetricsRecorder(meterRegistry);

    @Test
    void recordMessageConsumedRecordsCounterAndTimerWithOutcomeTag() {
        recorder.recordMessageConsumed(true, Duration.ofMillis(50));
        recorder.recordMessageConsumed(false, Duration.ofMillis(20));

        assertThat(meterRegistry.counter("audit.messaging.consumed", "outcome", "success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("audit.messaging.consumed", "outcome", "failure").count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer("audit.messaging.consumed.duration", "outcome", "success").count())
                .isEqualTo(1L);
    }

    @Test
    void recordAppendRecordsCounterAndTimerWithOutcomeTag() {
        recorder.recordAppend(true, Duration.ofMillis(5));
        recorder.recordAppend(false, Duration.ofMillis(5));

        assertThat(meterRegistry.counter("audit.ledger.append", "outcome", "success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("audit.ledger.append", "outcome", "failure").count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer("audit.ledger.append.duration", "outcome", "failure").count()).isEqualTo(1L);
    }

    @Test
    void recordVerificationRecordsCleanAndBrokenCountersAndDuration() {
        recorder.recordVerification(3, 1, Duration.ofSeconds(2));

        assertThat(meterRegistry.counter("audit.verification.partitions", "status", "clean").count())
                .isEqualTo(3.0);
        assertThat(meterRegistry.counter("audit.verification.partitions", "status", "broken").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.timer("audit.verification.duration").count()).isEqualTo(1L);
    }

    @Test
    void recordAnchorRecordsSuccessAndFailureCountersAndDuration() {
        recorder.recordAnchor(2, 1, Duration.ofSeconds(3));

        assertThat(meterRegistry.counter("audit.anchor.partitions", "status", "success").count())
                .isEqualTo(2.0);
        assertThat(meterRegistry.counter("audit.anchor.partitions", "status", "failure").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.timer("audit.anchor.duration").count()).isEqualTo(1L);
    }

    @Test
    void recordPartitionLockWaitRecordsTimer() {
        recorder.recordPartitionLockWait(Duration.ofMillis(42));

        assertThat(meterRegistry.timer("audit.jpa.partition-lock.wait").count()).isEqualTo(1L);
    }

    @Test
    void recordStalenessRejectionRecordsCounter() {
        recorder.recordStalenessRejection("some-partition");
        recorder.recordStalenessRejection("some-partition");

        assertThat(meterRegistry.counter("audit.jpa.partition-lock.staleness-rejections").count()).isEqualTo(2.0);
    }

    @Test
    void recordLockTimeoutRecordsCounter() {
        recorder.recordLockTimeout("some-partition");

        assertThat(meterRegistry.counter("audit.jpa.partition-lock.timeouts").count()).isEqualTo(1.0);
    }

    @Test
    void recordShardOwnershipAcceptedAndMismatchRecordSeparateOutcomeCounts() {
        recorder.recordShardOwnershipAccepted("some-partition");
        recorder.recordShardOwnershipAccepted("some-partition");
        recorder.recordShardMismatch("some-partition");

        assertThat(meterRegistry.counter("audit.rabbit.shard.ownership", "outcome", "accepted").count())
                .isEqualTo(2.0);
        assertThat(meterRegistry.counter("audit.rabbit.shard.ownership", "outcome", "mismatch").count())
                .isEqualTo(1.0);
    }
}
