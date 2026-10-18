package com.company.audit.spring.persistence.jpa.adapter;

import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.core.port.ChainRepository;
import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.persistence.jpa.mapper.AuditImmutableMapper;
import com.company.audit.spring.persistence.jpa.repository.SpringDataAuditImmutableRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * A {@link ChainRepository} backed by a Postgres table via Spring Data JPA.
 *
 * <p>Not component-scanned: registered explicitly as a bean by
 * {@link com.company.audit.spring.autoconfigure.AuditJpaAutoConfiguration}, matching how a
 * starter's internal wiring should be declared rather than relying on the consuming
 * application's component scan reaching into this package.
 *
 * <p><b>Cross-instance serialization via Postgres advisory locks.</b> Real deployments run
 * multiple instances per region, each its own JVM — a Phase 2 in-JVM {@code ReentrantLock} only
 * ever serialized writers within one process, leaving two independent instances free to both
 * read the same tip for a partition and race to append. {@code audit-core}'s
 * {@code DefaultAuditAppender} (frozen since Phase 1) calls {@link #findTip} and then
 * {@link #append} as two separate, uncoordinated port calls per attempt — because those two
 * calls belong to a frozen module, they cannot be merged there, so instead {@link #append} itself
 * acquires a transaction-scoped Postgres advisory lock
 * ({@code pg_advisory_xact_lock(hashtext(partitionKey))}) as its very first step. Every instance
 * already shares one Postgres; this gives cross-instance mutual exclusion for free, tied to the
 * existing {@code @Transactional} boundary, with no new coordination infrastructure (no Redis, no
 * ZooKeeper) — see {@code docs/adr/0001-distributed-ordering-advisory-locks.md} for the full
 * reasoning, including why this is additional pessimistic coordination layered on top of the
 * existing optimistic {@code UNIQUE} constraint, not a replacement for it.
 *
 * <p><b>The lock alone only narrows the race window — it does not close it.</b> Both of two
 * racing transactions could have read a stale tip <em>before</em> either acquired the lock. Once
 * this method holds the lock, it re-reads the partition's actual current tip and compares it
 * against what the incoming {@link ChainedRecord} was computed from; a mismatch means the
 * caller's earlier unlocked {@link #findTip} read has gone stale in the time it took to acquire
 * the lock, and is rejected via {@link SeqConflictException} <em>before</em> any insert is
 * attempted — not left to be caught reactively by the {@code UNIQUE(partition_key, seq)}
 * constraint at insert time. That constraint remains the true correctness guarantee regardless (a
 * 32-bit {@code hashtext()} collision between two unrelated partition keys is an accepted,
 * documented liveness tradeoff — occasional unnecessary serialization between unrelated tenants,
 * never incorrect data — see the ADR for the 64-bit evolution path if collision rates ever become
 * measurable), but the proactive in-lock check is what actually prevents wasted round-trips and
 * gives a retry a much higher chance of succeeding on the very next attempt.
 *
 * <p>Uses {@code pg_advisory_xact_lock}, never the session-scoped {@code pg_advisory_lock}: the
 * transaction-scoped form releases automatically at commit/rollback, tied to the existing
 * {@code @Transactional} boundary, whereas the session-scoped form requires a manual unlock and
 * is a real liability under connection pooling (a crashed or misbehaving caller could hold a lock
 * forever).
 */
public class JpaChainRepository implements ChainRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaChainRepository.class);

    private final SpringDataAuditImmutableRepository springDataRepository;
    private final AuditImmutableMapper mapper;
    private final AuditMetricsRecorder metricsRecorder;
    private final Duration partitionLockTimeout;

    /**
     * Per-thread, per-partition attempt counters used only for the {@code DEBUG} log line below —
     * not a correctness mechanism. Entries are removed on a successful append, but a call that
     * throws (lock timeout or staleness rejection) leaves its entry in place so the next retry
     * within the same logical {@code DefaultAuditAppender} retry loop (same thread, since that
     * loop is synchronous) sees a correctly incrementing count rather than resetting to 1 every
     * attempt.
     *
     * <p><b>Confirmed bounded, not unbounded, despite never being explicitly cleared on permanent
     * failure</b> (a caller that exhausts every retry and gives up leaves one stale entry behind
     * on that thread): this map's key space is the set of distinct partition keys that pooled
     * thread has ever permanently failed to append to, which is bounded by this deployment's real
     * partition cardinality — an operator-controlled, finite set — not by request volume or a
     * per-call unique key, so it cannot grow without bound as the system runs. A
     * release-readiness audit specifically checked this was not the classic unbounded-cache
     * hazard (e.g. keying by a per-request UUID) before accepting it as-is.
     */
    private final ThreadLocal<Map<String, Integer>> attemptCounters = ThreadLocal.withInitial(HashMap::new);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Creates a new repository adapter.
     *
     * @param springDataRepository the underlying Spring Data repository
     * @param mapper the mapper between domain and entity types
     * @param metricsRecorder records append outcomes and timing
     * @param partitionLockTimeout how long to wait to acquire a partition's advisory lock (and,
     *     via the same value, how long a single statement inside the transaction may run) before
     *     giving up
     */
    public JpaChainRepository(
            SpringDataAuditImmutableRepository springDataRepository,
            AuditImmutableMapper mapper,
            AuditMetricsRecorder metricsRecorder,
            Duration partitionLockTimeout) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
        this.metricsRecorder = metricsRecorder;
        this.partitionLockTimeout = partitionLockTimeout;
    }

    @Override
    public Optional<ChainedRecord> findTip(String partitionKey) {
        return springDataRepository.findTopByPartitionKeyOrderBySeqDesc(partitionKey).map(mapper::toDomain);
    }

    @Override
    // A DataIntegrityViolationException from the flush below poisons the underlying database
    // transaction (Postgres aborts it at the statement level); Spring's default rollback rules
    // only cover unchecked exceptions, so without this the interceptor tries to commit an
    // already-aborted transaction and raises UnexpectedRollbackException instead of the
    // SeqConflictException callers expect.
    @Transactional(rollbackFor = SeqConflictException.class)
    public void append(ChainedRecord record) throws SeqConflictException {
        String partitionKey = record.event().partitionKey();
        int attempt = attemptCounters.get().merge(partitionKey, 1, Integer::sum);
        long timeoutMillis = partitionLockTimeout.toMillis();

        setLocalTimeouts(timeoutMillis);

        Instant lockWaitStartedAt = Instant.now();
        try {
            acquirePartitionLock(partitionKey);
        } catch (PersistenceException e) {
            Duration lockWait = Duration.between(lockWaitStartedAt, Instant.now());
            log.debug(
                    "append lock-timeout partitionKey={} attempt={} lockWait={}", partitionKey, attempt, lockWait);
            metricsRecorder.recordLockTimeout(partitionKey);
            throw new SeqConflictException("Timed out waiting for partition lock: " + partitionKey, e);
        }
        Duration lockWait = Duration.between(lockWaitStartedAt, Instant.now());
        metricsRecorder.recordPartitionLockWait(lockWait);
        log.debug("append acquired-lock partitionKey={} attempt={} lockWait={}", partitionKey, attempt, lockWait);

        if (isStale(record, partitionKey)) {
            log.debug("append stale-rejection partitionKey={} attempt={}", partitionKey, attempt);
            metricsRecorder.recordStalenessRejection(partitionKey);
            throw new SeqConflictException("Tip changed since caller's read for partition: " + partitionKey, null);
        }

        try {
            springDataRepository.saveAndFlush(mapper.toEntity(record));
            metricsRecorder.recordAppend(true, Duration.between(lockWaitStartedAt, Instant.now()));
            attemptCounters.get().remove(partitionKey);
        } catch (DataIntegrityViolationException e) {
            metricsRecorder.recordAppend(false, Duration.between(lockWaitStartedAt, Instant.now()));
            throw new SeqConflictException("Duplicate seq " + record.seq() + " for partition " + partitionKey, e);
        }
    }

    @Override
    public List<ChainedRecord> findAllOrderedBySeq(String partitionKey) {
        return springDataRepository.findByPartitionKeyOrderBySeqAsc(partitionKey).stream()
                .map(mapper::toDomain)
                .toList();
    }

    // SET LOCAL does not accept bind parameters over JDBC; the value is our own configured
    // Duration, never caller input, so building the literal directly is safe.
    private void setLocalTimeouts(long timeoutMillis) {
        entityManager
                .createNativeQuery("SET LOCAL lock_timeout = '" + timeoutMillis + "ms'")
                .executeUpdate();
        // Recommended, not mandatory (see AuditProperties.Jpa#getPartitionLockTimeout): bounds a
        // hung freshness-check query or insert after the lock was already acquired, a different
        // failure mode than lock_timeout, which only bounds time spent waiting to acquire it.
        entityManager
                .createNativeQuery("SET LOCAL statement_timeout = '" + timeoutMillis + "ms'")
                .executeUpdate();
    }

    private void acquirePartitionLock(String partitionKey) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:partitionKey))")
                .setParameter("partitionKey", partitionKey)
                .getSingleResult();
    }

    private boolean isStale(ChainedRecord record, String partitionKey) {
        Optional<ChainedRecord> currentTip = findTip(partitionKey);
        long expectedPrevSeq = record.seq() - 1;
        return expectedPrevSeq == 0
                ? currentTip.isPresent()
                : currentTip.map(tip -> tip.seq() != expectedPrevSeq).orElse(true);
    }
}
