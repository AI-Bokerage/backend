package com.company.audit.spring.ingestion;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.port.EventStore;

/**
 * Composes {@link PartitionRegistry}, {@link AuditAppender}, and {@link EventStore} into a
 * single call, so application code stops having to manually sequence three port calls itself.
 *
 * <p><b>This is the recommended entry point for application code going forward.</b>
 * {@code audit-core}'s {@link AuditAppender} remains available as a lower-level ledger primitive
 * for callers who genuinely only want the tamper-evident hash chain, with no rich copy — for
 * example, a caller that has its own reasons not to write to Mongo. Once more than one CPMS
 * service consumes this starter, that distinction matters more than it does today, so it is made
 * explicit here rather than left to be inferred from whichever class happens to be more
 * convenient at a given call site.
 *
 * <p>Write order is deliberate: Postgres (the tamper-evident source of truth) is written first,
 * inside its own Spring-managed transaction (via {@link PartitionRegistry#resolve} and
 * {@link AuditAppender#append}, both {@code @Transactional} in their JPA implementations). The
 * Mongo write happens only after that transaction has committed successfully, and only as a
 * plain, synchronous call — if it throws, the exception propagates to the caller rather than
 * being caught and logged. This ordering is intentionally synchronous for now: a later phase
 * will replace it with an outbox-based asynchronous implementation. Do not "optimize" this into
 * something silently eventually-consistent in the meantime — that would be undoing a deliberate
 * decision, not fixing an oversight. Likewise, do not attempt to wrap both writes in a single
 * distributed (XA) transaction: that is a different, heavier consistency model than the
 * synchronous-but-single-direction, fail-loud-on-the-second-write behavior this class provides.
 */
public class AuditRecorder {

    private final PartitionRegistry partitionRegistry;
    private final AuditAppender auditAppender;
    private final EventStore eventStore;

    /**
     * Creates a new recorder.
     *
     * @param partitionRegistry resolves the stable partition context for an event's partition
     * @param auditAppender appends the event to the tamper-evident ledger
     * @param eventStore persists the full event, payload included, after the ledger append
     *     commits
     */
    public AuditRecorder(PartitionRegistry partitionRegistry, AuditAppender auditAppender, EventStore eventStore) {
        this.partitionRegistry = partitionRegistry;
        this.auditAppender = auditAppender;
        this.eventStore = eventStore;
    }

    /**
     * Records an event: resolves its partition, appends it to the ledger, then persists the
     * full rich copy.
     *
     * @param event the event to record
     * @return the chained record produced by the ledger append
     */
    public ChainedRecord record(AuditEvent event) {
        PartitionContext partitionContext = partitionRegistry.resolve(event.partitionKey());
        ChainedRecord record = auditAppender.append(event, partitionContext);
        eventStore.save(event, record);
        return record;
    }
}
