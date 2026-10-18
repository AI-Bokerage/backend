package com.company.audit.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.exception.ChainIntegrityException;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.core.fixture.InMemoryChainRepository;
import com.company.audit.core.internal.crypto.ChainHasher;
import com.company.audit.core.port.ChainRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultAuditAppenderTest {

    private final InMemoryChainRepository repository = new InMemoryChainRepository();

    @Test
    void seqIncrementsIndependentlyPerPartitionAndHashesLinkCorrectly() {
        Instant fixedInstant = Instant.parse("2024-06-01T12:00:00Z");
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        AuditAppender appender = AuditAppender.create(repository, clock);

        String partitionA = "partition-a";
        String partitionB = "partition-b";
        PartitionContext contextA = new PartitionContext(partitionA, fixedInstant);
        PartitionContext contextB = new PartitionContext(partitionB, fixedInstant);

        List<ChainedRecord> recordsA = new ArrayList<>();
        List<ChainedRecord> recordsB = new ArrayList<>();

        for (int i = 0; i < 60; i++) {
            String partitionKey = i % 2 == 0 ? partitionA : partitionB;
            PartitionContext context = i % 2 == 0 ? contextA : contextB;
            AuditEvent event = buildEvent(partitionKey, i, fixedInstant);

            ChainedRecord record = appender.append(event, context);

            if (i % 2 == 0) {
                recordsA.add(record);
            } else {
                recordsB.add(record);
            }
            assertThat(record.recordedAt()).isEqualTo(fixedInstant);
        }

        assertSeqAndLinkage(recordsA, contextA);
        assertSeqAndLinkage(recordsB, contextB);
    }

    @Test
    void exhaustingRetriesOnPersistentSeqConflictThrowsChainIntegrityException() {
        AtomicInteger attempts = new AtomicInteger();
        ChainRepository alwaysConflicting = new ChainRepository() {
            @Override
            public Optional<ChainedRecord> findTip(String partitionKey) {
                return Optional.empty();
            }

            @Override
            public void append(ChainedRecord record) throws SeqConflictException {
                attempts.incrementAndGet();
                throw new SeqConflictException("always conflicts");
            }

            @Override
            public List<ChainedRecord> findAllOrderedBySeq(String partitionKey) {
                return List.of();
            }
        };
        Instant fixedInstant = Instant.parse("2024-06-01T12:00:00Z");
        AuditAppender appender = AuditAppender.create(alwaysConflicting, Clock.fixed(fixedInstant, ZoneOffset.UTC));
        PartitionContext context = new PartitionContext("conflict-partition", fixedInstant);

        assertThatThrownBy(() -> appender.append(buildEvent("conflict-partition", 0, fixedInstant), context))
                .isInstanceOf(ChainIntegrityException.class)
                .hasCauseInstanceOf(SeqConflictException.class);
        assertThat(attempts.get()).isEqualTo(3);
    }

    private void assertSeqAndLinkage(List<ChainedRecord> records, PartitionContext context) {
        assertThat(records).hasSize(30);
        assertThat(records.get(0).prevEventHash()).isEqualTo(new ChainHasher().computeGenesis(context));
        for (int i = 0; i < records.size(); i++) {
            ChainedRecord record = records.get(i);
            assertThat(record.seq()).isEqualTo(i + 1L);
            if (i > 0) {
                assertThat(record.prevEventHash()).isEqualTo(records.get(i - 1).eventHash());
            }
        }
    }

    private AuditEvent buildEvent(String partitionKey, int index, Instant occurredAt) {
        return AuditEvent.builder()
                .id("event-" + partitionKey + "-" + index)
                .partitionKey(partitionKey)
                .eventType("TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(occurredAt)
                .payload(Map.of("index", index))
                .build();
    }
}
