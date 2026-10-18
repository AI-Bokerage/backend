package com.company.audit.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.core.fixture.InMemoryChainRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultAuditVerifierTest {

    private final InMemoryChainRepository repository = new InMemoryChainRepository();
    private final Instant fixedInstant = Instant.parse("2024-06-01T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    private final String partitionKey = "verify-partition";
    private final PartitionContext partitionContext = new PartitionContext(partitionKey, fixedInstant);

    @Test
    void intactChainVerifiesAsOk() {
        AuditAppender appender = AuditAppender.create(repository, clock);
        for (int i = 0; i < 10; i++) {
            appender.append(buildEvent(i), partitionContext);
        }

        AuditVerifier verifier = AuditVerifier.create(repository);
        VerificationResult result = verifier.verify(partitionKey, partitionContext);

        assertThat(result.status()).isEqualTo(AuditChainStatus.OK);
        assertThat(result.breakAtSeq()).isEmpty();
    }

    @Test
    void tamperedMiddleRecordIsDetectedAsHashMismatch() throws NoSuchAlgorithmException {
        AuditAppender appender = AuditAppender.create(repository, clock);
        ChainedRecord tampered = null;
        for (int i = 0; i < 10; i++) {
            ChainedRecord record = appender.append(buildEvent(i), partitionContext);
            if (i == 4) {
                tampered = record;
            }
        }

        HashValue bogusHash = HashValue.of(
                MessageDigest.getInstance("SHA-256").digest("tampered".getBytes()));
        ChainedRecord corrupted = new ChainedRecord(
                tampered.event(), tampered.seq(), tampered.prevEventHash(), tampered.payloadHash(),
                bogusHash, tampered.recordedAt());
        repository.corrupt(partitionKey, tampered.seq(), corrupted);

        AuditVerifier verifier = AuditVerifier.create(repository);
        VerificationResult result = verifier.verify(partitionKey, partitionContext);

        assertThat(result.status()).isEqualTo(AuditChainStatus.HASH_MISMATCH);
        assertThat(result.breakAtSeq()).contains(tampered.seq());
    }

    @Test
    void gapInSequenceIsDetectedAsBreakAtSeq() {
        AuditAppender appender = AuditAppender.create(repository, clock);
        ChainedRecord gapRecord = null;
        for (int i = 0; i < 10; i++) {
            ChainedRecord record = appender.append(buildEvent(i), partitionContext);
            if (record.seq() == 4) {
                gapRecord = record;
            }
        }

        // Move the record at seq 4 out to seq 100, leaving a gap at 4.
        ChainedRecord movedOut = new ChainedRecord(
                gapRecord.event(), 100L, gapRecord.prevEventHash(), gapRecord.payloadHash(),
                gapRecord.eventHash(), gapRecord.recordedAt());
        repository.corrupt(partitionKey, 4L, movedOut);

        AuditVerifier verifier = AuditVerifier.create(repository);
        VerificationResult result = verifier.verify(partitionKey, partitionContext);

        assertThat(result.status()).isEqualTo(AuditChainStatus.BREAK_AT_SEQ);
        assertThat(result.breakAtSeq()).contains(5L);
    }

    @Test
    void duplicateSeqIsDetected() {
        AuditAppender appender = AuditAppender.create(repository, clock);
        ChainedRecord seqFour = null;
        ChainedRecord seqFive = null;
        for (int i = 0; i < 10; i++) {
            ChainedRecord record = appender.append(buildEvent(i), partitionContext);
            if (record.seq() == 4) {
                seqFour = record;
            }
            if (record.seq() == 5) {
                seqFive = record;
            }
        }

        // Duplicate seq 4's number onto what used to be seq 5.
        ChainedRecord duplicated = new ChainedRecord(
                seqFive.event(), 4L, seqFour.prevEventHash(), seqFive.payloadHash(),
                seqFive.eventHash(), seqFive.recordedAt());
        repository.corrupt(partitionKey, 5L, duplicated);

        AuditVerifier verifier = AuditVerifier.create(repository);
        VerificationResult result = verifier.verify(partitionKey, partitionContext);

        assertThat(result.status()).isEqualTo(AuditChainStatus.DUPLICATE_SEQ);
        assertThat(result.breakAtSeq()).contains(4L);
    }

    @Test
    void tamperedPrevHashIsDetectedAsBreakAtSeqBeforeRecomputingEventHash() throws NoSuchAlgorithmException {
        AuditAppender appender = AuditAppender.create(repository, clock);
        ChainedRecord tampered = null;
        for (int i = 0; i < 10; i++) {
            ChainedRecord record = appender.append(buildEvent(i), partitionContext);
            if (i == 4) {
                tampered = record;
            }
        }

        HashValue bogusPrevHash = HashValue.of(
                MessageDigest.getInstance("SHA-256").digest("bogus-prev".getBytes()));
        ChainedRecord corrupted = new ChainedRecord(
                tampered.event(), tampered.seq(), bogusPrevHash, tampered.payloadHash(),
                tampered.eventHash(), tampered.recordedAt());
        repository.corrupt(partitionKey, tampered.seq(), corrupted);

        AuditVerifier verifier = AuditVerifier.create(repository);
        VerificationResult result = verifier.verify(partitionKey, partitionContext);

        assertThat(result.status()).isEqualTo(AuditChainStatus.BREAK_AT_SEQ);
        assertThat(result.breakAtSeq()).contains(tampered.seq());
    }

    @Test
    void verifyingWithMismatchedPartitionContextBreaksAtGenesis() {
        AuditAppender appender = AuditAppender.create(repository, clock);
        for (int i = 0; i < 5; i++) {
            appender.append(buildEvent(i), partitionContext);
        }

        PartitionContext wrongContext =
                new PartitionContext(partitionKey, fixedInstant.plusSeconds(1));

        AuditVerifier verifier = AuditVerifier.create(repository);
        VerificationResult result = verifier.verify(partitionKey, wrongContext);

        assertThat(result.status()).isEqualTo(AuditChainStatus.BREAK_AT_SEQ);
        assertThat(result.breakAtSeq()).contains(1L);
    }

    private AuditEvent buildEvent(int index) {
        return AuditEvent.builder()
                .id("event-" + index)
                .partitionKey(partitionKey)
                .eventType("TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(fixedInstant)
                .payload(Map.of("index", index))
                .build();
    }
}
