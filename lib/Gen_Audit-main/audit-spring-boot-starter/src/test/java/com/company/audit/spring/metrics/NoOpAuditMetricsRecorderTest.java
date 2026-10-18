package com.company.audit.spring.metrics;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NoOpAuditMetricsRecorderTest {

    private final NoOpAuditMetricsRecorder recorder = new NoOpAuditMetricsRecorder();

    @Test
    void everyMethodDoesNothingObservableAndNeverThrows() {
        assertThatCode(() -> {
                    recorder.recordMessageConsumed(true, Duration.ofMillis(1));
                    recorder.recordMessageConsumed(false, Duration.ofMillis(1));
                    recorder.recordAppend(true, Duration.ofMillis(1));
                    recorder.recordAppend(false, Duration.ofMillis(1));
                    recorder.recordVerification(3, 1, Duration.ofSeconds(1));
                    recorder.recordAnchor(2, 1, Duration.ofSeconds(1));
                    recorder.recordPartitionLockWait(Duration.ofMillis(1));
                    recorder.recordStalenessRejection("some-partition");
                    recorder.recordLockTimeout("some-partition");
                    recorder.recordShardOwnershipAccepted("some-partition");
                    recorder.recordShardMismatch("some-partition");
                })
                .doesNotThrowAnyException();
    }
}
