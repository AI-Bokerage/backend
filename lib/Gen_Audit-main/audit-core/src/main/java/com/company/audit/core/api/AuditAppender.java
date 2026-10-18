package com.company.audit.core.api;

import com.company.audit.core.internal.DefaultAuditAppender;
import com.company.audit.core.port.ChainRepository;
import java.time.Clock;

/**
 * Appends events to a partition's tamper-evident hash chain.
 *
 * <p>Obtain instances via {@link #create}; the implementation is intentionally hidden.
 */
public interface AuditAppender {

    /**
     * Creates an {@code AuditAppender} backed by the given repository, using the system UTC
     * clock.
     *
     * @param repository the storage port to append records to
     * @return a new {@code AuditAppender}
     */
    static AuditAppender create(ChainRepository repository) {
        return new DefaultAuditAppender(repository, Clock.systemUTC());
    }

    /**
     * Creates an {@code AuditAppender} backed by the given repository and clock.
     *
     * @param repository the storage port to append records to
     * @param clock the clock used to timestamp appended records
     * @return a new {@code AuditAppender}
     */
    static AuditAppender create(ChainRepository repository, Clock clock) {
        return new DefaultAuditAppender(repository, clock);
    }

    /**
     * Creates an {@code AuditAppender} backed by the given repository and clock, retrying up to
     * {@code maxAttempts} times on a sequence conflict instead of the default 3.
     *
     * <p>The default (via {@link #create(ChainRepository, Clock)}) remains 3 and is unchanged by
     * this overload's existence — only callers who explicitly opt in by calling this overload get
     * a different bound. Added in response to measured evidence (see {@code audit-spring-boot-starter}'s
     * Phase 8 distributed-ordering work) that under genuinely high concurrent contention on one
     * partition, a caller pairing {@link ChainRepository#findTip} and
     * {@link ChainRepository#append} as two uncoordinated calls can need a number of retries that
     * scales with the number of contending callers, not a small constant — see this interface's
     * implementation-side Javadoc discussion for why that two-call protocol itself is unchanged
     * this phase.
     *
     * @param repository the storage port to append records to
     * @param clock the clock used to timestamp appended records
     * @param maxAttempts the maximum number of attempts before giving up; must be at least 1
     * @return a new {@code AuditAppender}
     */
    static AuditAppender create(ChainRepository repository, Clock clock, int maxAttempts) {
        return new DefaultAuditAppender(repository, clock, maxAttempts);
    }

    /**
     * Appends the given event to its partition's chain, linking it to the current tip (or the
     * partition's genesis hash if the chain is empty).
     *
     * @param event the event to append
     * @param partitionContext the context of the partition the event belongs to
     * @return the resulting chained record, including its assigned sequence number and hashes
     */
    ChainedRecord append(AuditEvent event, PartitionContext partitionContext);
}
