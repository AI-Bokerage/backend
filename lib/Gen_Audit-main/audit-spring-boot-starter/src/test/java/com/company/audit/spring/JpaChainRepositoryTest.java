package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.spring.persistence.jpa.adapter.JpaChainRepository;
import com.company.audit.spring.support.AbstractPostgresIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestApplication.class)
class JpaChainRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JpaChainRepository repository;

    @Test
    void appendFindTipAndFindAllOrderedBySeqBehaveCorrectly() throws Exception {
        String partitionKey = "jpa-chain-repository-test-" + UUID.randomUUID();
        ChainedRecord first = buildRecord(partitionKey, 1, genesisHash());
        ChainedRecord second = buildRecord(partitionKey, 2, first.eventHash());

        repository.append(first);
        repository.append(second);

        assertThat(repository.findTip(partitionKey)).isPresent();
        assertThat(repository.findTip(partitionKey).get().seq()).isEqualTo(2L);

        var all = repository.findAllOrderedBySeq(partitionKey);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).seq()).isEqualTo(1L);
        assertThat(all.get(1).seq()).isEqualTo(2L);
        assertThat(all.get(0).eventHash()).isEqualTo(first.eventHash());
        assertThat(all.get(1).eventHash()).isEqualTo(second.eventHash());
    }

    @Test
    void appendingDuplicateSeqThrowsSeqConflictException() throws Exception {
        // seq=1 (not an arbitrary later value) is required here since Phase 8's in-lock
        // freshness check now rejects a candidate whose implied prevSeq doesn't match the
        // partition's actual current tip — a fresh partition's only valid first seq is 1, so a
        // record claiming seq=10 against an empty partition would (correctly) be rejected as
        // stale before ever reaching the duplicate-seq/UNIQUE-constraint check this test exists
        // to prove.
        String partitionKey = "jpa-chain-repository-test-" + UUID.randomUUID();
        ChainedRecord first = buildRecord(partitionKey, 1, genesisHash());
        repository.append(first);

        ChainedRecord duplicateSeq = buildRecord(partitionKey, 1, genesisHash());

        assertThatThrownBy(() -> repository.append(duplicateSeq)).isInstanceOf(SeqConflictException.class);
    }

    private ChainedRecord buildRecord(String partitionKey, long seq, HashValue prevHash)
            throws NoSuchAlgorithmException {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(partitionKey)
                .eventType("TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(Instant.parse("2024-06-01T12:00:00Z"))
                .payload(Map.of("seq", seq))
                .build();
        HashValue payloadHash = HashValue.of(sha256("payload-" + seq));
        HashValue eventHash = HashValue.of(sha256("event-" + seq));
        return new ChainedRecord(event, seq, prevHash, payloadHash, eventHash, Instant.parse("2024-06-01T12:00:00Z"));
    }

    private HashValue genesisHash() throws NoSuchAlgorithmException {
        return HashValue.of(sha256("genesis"));
    }

    private byte[] sha256(String input) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(input.getBytes());
    }
}
