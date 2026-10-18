package com.company.audit.spring.anchor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.core.port.ChainRepository;
import com.company.audit.core.port.ObjectLockPort;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnchorPublisherTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2024-06-01T12:00:00Z");

    private final Map<String, ChainedRecord> tipsByPartition = new HashMap<>();
    private final FakeObjectLockPort fakeObjectLockPort = new FakeObjectLockPort();

    private final ChainRepository chainRepository = new ChainRepository() {
        @Override
        public Optional<ChainedRecord> findTip(String partitionKey) {
            return Optional.ofNullable(tipsByPartition.get(partitionKey));
        }

        @Override
        public void append(ChainedRecord record) throws SeqConflictException {
            tipsByPartition.put(record.event().partitionKey(), record);
        }

        @Override
        public List<ChainedRecord> findAllOrderedBySeq(String partitionKey) {
            return List.of();
        }
    };

    private final AnchorPublisher anchorPublisher =
            new AnchorPublisher(chainRepository, fakeObjectLockPort, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

    @Test
    void publishForPartitionWithExistingChainRecordsCorrectPartitionKeyAndTipHash() throws Exception {
        String partitionKey = "anchor-publisher-test-" + UUID.randomUUID();
        ChainedRecord tip = buildRecord(partitionKey);
        tipsByPartition.put(partitionKey, tip);

        String storageReference = anchorPublisher.publish(partitionKey);

        assertThat(storageReference).isEqualTo("fake-ref-1");
        assertThat(fakeObjectLockPort.lastPartitionKey).isEqualTo(partitionKey);
        assertThat(fakeObjectLockPort.lastTipHash).isEqualTo(tip.eventHash());
        assertThat(fakeObjectLockPort.lastPublishedAt).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void publishForPartitionWithNoChainThrowsIllegalStateException() {
        String partitionKey = "anchor-publisher-test-no-chain-" + UUID.randomUUID();

        assertThatThrownBy(() -> anchorPublisher.publish(partitionKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(partitionKey);
    }

    private ChainedRecord buildRecord(String partitionKey) throws Exception {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(partitionKey)
                .eventType("ANCHOR_TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(FIXED_INSTANT)
                .payload(Map.of())
                .build();
        HashValue hash = HashValue.of(MessageDigest.getInstance("SHA-256").digest("anchor-test".getBytes()));
        return new ChainedRecord(event, 1L, hash, hash, hash, FIXED_INSTANT);
    }

    private static final class FakeObjectLockPort implements ObjectLockPort {

        private String lastPartitionKey;
        private HashValue lastTipHash;
        private Instant lastPublishedAt;

        @Override
        public String publishAnchor(String partitionKey, HashValue tipHash, Instant publishedAt) {
            this.lastPartitionKey = partitionKey;
            this.lastTipHash = tipHash;
            this.lastPublishedAt = publishedAt;
            return "fake-ref-1";
        }
    }
}
