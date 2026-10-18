package com.company.audit.core.internal;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.exception.ChainIntegrityException;
import com.company.audit.core.api.exception.SeqConflictException;
import com.company.audit.core.internal.crypto.CanonicalJsonSerializer;
import com.company.audit.core.internal.crypto.ChainHasher;
import com.company.audit.core.port.ChainRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default implementation of {@link AuditAppender}.
 *
 * <p>Not part of the public API — this class is public only so that {@link AuditAppender#create}
 * can construct it from the {@code api} package; it is kept out of the public surface by not
 * being exported from this module's {@code module-info.java}. Consumers obtain instances via
 * {@link AuditAppender#create}, never by naming this class.
 *
 * <p><b>Jittered backoff between retry attempts.</b> Added in response to a Phase 8 finding in
 * {@code audit-spring-boot-starter}: under N-way synchronized contention on one partition, every
 * contender's retry loop tends to re-read the same freshly-changed tip at nearly the same moment,
 * so contenders keep recolliding round after round instead of naturally spreading out — the
 * actual cause behind the high retry-exhaustion rate {@code audit-jpa}'s advisory-lock mechanism
 * measured at high contention, not merely a symptom a larger {@code maxAttempts} alone should
 * paper over. A small randomized delay before each retry (not before the first attempt) breaks
 * that synchronization: contenders that would otherwise retry in lock-step now retry at
 * scattered times, so a given round is far less likely to have every remaining contender collide
 * on the same stale read again. This is confined entirely to this {@code internal} class — no
 * change to {@link AuditAppender}'s public signature, no version-bump implication (see
 * {@code VERSIONING.md}: changes confined to {@code audit-core}'s {@code internal} package carry
 * no compatibility-surface implication).
 */
public final class DefaultAuditAppender implements AuditAppender {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    // Full-jitter exponential backoff (sleep = random(0, min(cap, base * 2^attempt))): small
    // enough not to dominate append latency even at the maximum attempt count this project
    // currently configures (audit.jpa.append-max-attempts=20), large enough to meaningfully
    // desynchronize contenders that would otherwise retry in lock-step.
    private static final long BACKOFF_BASE_MILLIS = 5;
    private static final long BACKOFF_CAP_MILLIS = 200;

    private final ChainRepository repository;
    private final Clock clock;
    private final int maxAttempts;
    private final ChainHasher chainHasher = new ChainHasher();
    private final CanonicalJsonSerializer canonicalJsonSerializer = new CanonicalJsonSerializer();

    /**
     * Creates a new appender with the default 3-attempt retry bound. Not part of the public API;
     * use {@link AuditAppender#create}.
     *
     * @param repository the storage port to append records to
     * @param clock the clock used to timestamp appended records
     */
    public DefaultAuditAppender(ChainRepository repository, Clock clock) {
        this(repository, clock, DEFAULT_MAX_ATTEMPTS);
    }

    /**
     * Creates a new appender with a caller-specified retry bound. Not part of the public API;
     * use {@link AuditAppender#create}.
     *
     * @param repository the storage port to append records to
     * @param clock the clock used to timestamp appended records
     * @param maxAttempts the maximum number of attempts before giving up; must be at least 1
     */
    public DefaultAuditAppender(ChainRepository repository, Clock clock, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
        }
        this.repository = repository;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public ChainedRecord append(AuditEvent event, PartitionContext partitionContext) {
        SeqConflictException lastConflict = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                if (!sleepWithJitter(attempt - 1)) {
                    break;
                }
            }
            ChainedRecord candidate = buildCandidate(event, partitionContext);
            try {
                repository.append(candidate);
                return candidate;
            } catch (SeqConflictException e) {
                lastConflict = e;
            }
        }
        throw new ChainIntegrityException(
                "Exhausted " + maxAttempts + " append attempts for partition "
                        + event.partitionKey() + " due to sequence conflicts",
                lastConflict);
    }

    /**
     * Sleeps for a random duration bounded by an exponentially growing cap, one fewer prior
     * attempt shorter than the next. Returns {@code false} (having restored the interrupt flag)
     * if interrupted while sleeping, signaling the caller to stop retrying rather than proceed
     * having not actually waited.
     *
     * @param priorAttempts how many attempts have already been made
     * @return {@code true} if the sleep completed normally
     */
    private boolean sleepWithJitter(int priorAttempts) {
        long cap = Math.min(BACKOFF_CAP_MILLIS, BACKOFF_BASE_MILLIS * (1L << Math.min(priorAttempts, 10)));
        long delayMillis = ThreadLocalRandom.current().nextLong(cap + 1);
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ChainedRecord buildCandidate(AuditEvent event, PartitionContext partitionContext) {
        Optional<ChainedRecord> tip = repository.findTip(event.partitionKey());
        long nextSeq = tip.map(ChainedRecord::seq).orElse(0L) + 1;
        HashValue prevHash = tip.map(ChainedRecord::eventHash)
                .orElseGet(() -> chainHasher.computeGenesis(partitionContext));
        HashValue payloadHash =
                chainHasher.computePayloadHash(canonicalJsonSerializer.canonicalize(event.payload()));
        HashValue eventHash = chainHasher.computeEventHash(prevHash, payloadHash);
        return new ChainedRecord(event, nextSeq, prevHash, payloadHash, eventHash, clock.instant());
    }
}
